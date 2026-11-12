package com.holidays.plan.security;

import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Risolve sempre l'utente dal token: mai da id passati dal client. */
@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    /** Utente corrente; se deve ancora cambiare la password, blocca ogni operazione tranne il cambio. */
    public User get() {
        User u = getAllowingPasswordChange();
        if (u.isMustChangePassword()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Devi cambiare la password prima di continuare");
        }
        return u;
    }

    /** Per /auth/me e /auth/password: consentiti anche se la password va cambiata. */
    public User getAllowingPasswordChange() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessione non valida");
        }
        long id;
        try {
            id = Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessione non valida");
        }
        User u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessione non valida"));
        if (!u.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Utente disattivato");
        }
        // Il ruolo nel token deve coincidere con quello attuale: se l'admin lo cambia, il vecchio token decade.
        boolean roleMatches = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + u.getRole().name()));
        if (!roleMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Ruolo cambiato, accedi di nuovo");
        }
        return u;
    }
}
