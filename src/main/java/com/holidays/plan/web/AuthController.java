package com.holidays.plan.web;

import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.security.LoginAttemptService;
import com.holidays.plan.security.TokenService;
import com.holidays.plan.service.ApiException;
import com.holidays.plan.web.dto.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    // Hash fittizio per uniformare i tempi di risposta quando l'utente non esiste.
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEeO5Y1YVwZ2g3b7bZJp1oZx9xZbqk8dS1e";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final LoginAttemptService attempts;
    private final CurrentUser currentUser;

    public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens,
                          LoginAttemptService attempts, CurrentUser currentUser) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.attempts = attempts;
        this.currentUser = currentUser;
    }

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 200) String password) {}

    public record LoginResponse(String token, Instant expiresAt, UserSummary user) {}

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 200) String newPassword) {}

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@Valid @RequestBody LoginRequest in) {
        if (attempts.isLocked(in.username())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Troppi tentativi falliti, riprova tra qualche minuto");
        }
        User u = users.findByUsernameIgnoreCase(in.username().trim()).orElse(null);
        boolean ok = encoder.matches(in.password(), u == null ? DUMMY_HASH : u.getPasswordHash());
        if (u == null || !ok || !u.isActive()) {
            attempts.failed(in.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenziali non valide");
        }
        attempts.succeeded(in.username());
        TokenService.IssuedToken t = tokens.issue(u);
        return new LoginResponse(t.token(), t.expiresAt(), UserSummary.of(u));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserSummary me() {
        return UserSummary.of(currentUser.getAllowingPasswordChange());
    }

    @PostMapping("/password")
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePassword in) {
        User u = currentUser.getAllowingPasswordChange();
        if (!encoder.matches(in.currentPassword(), u.getPasswordHash())) throw ApiException.bad("Password attuale errata");
        if (encoder.matches(in.newPassword(), u.getPasswordHash())) throw ApiException.bad("La nuova password deve essere diversa da quella attuale");
        u.setPasswordHash(encoder.encode(in.newPassword()));
        u.setMustChangePassword(false);
        users.save(u);
    }
}
