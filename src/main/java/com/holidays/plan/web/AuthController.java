package com.holidays.plan.web;

import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.security.LoginAttemptService;
import com.holidays.plan.security.TokenService;
import com.holidays.plan.service.ApiException;
import com.holidays.plan.service.PasswordResetService;
import com.holidays.plan.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.holidays.plan.web.dto.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

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
    private final PasswordResetService reset;
    private final SettingsService settings;

    public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens,
                          LoginAttemptService attempts, CurrentUser currentUser,
                          PasswordResetService reset, SettingsService settings) {
        this.reset = reset;
        this.settings = settings;
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.attempts = attempts;
        this.currentUser = currentUser;
    }

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 200) String password) {}

    public record LoginResponse(String token, Instant expiresAt, UserSummary user) {}

    public record ForgotRequest(@Size(max = 100) String username, @Size(max = 254) String email) {}

    public record ResetRequest(@Size(max = 100) String token, @Size(max = 200) String password, @Size(max = 200) String confirm) {}

    /** Opzioni visibili anche senza login: reset password attivo, link dell'APK. */
    public record Options(boolean passwordReset, String apkUrl) {}

    public static final String FORGOT_SENT = "Se username ed email corrispondono a un utente, riceverai a breve un'email con il link per scegliere una nuova password. Il link vale 30 minuti e si puo' usare una sola volta. Controlla anche la cartella spam.";

    @GetMapping("/options")
    public Options options() {
        return new Options(reset.isAvailable(), settings.get().apkUrl());
    }

    @PostMapping("/password-forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest in, HttpServletRequest req) {
        reset.requestLink(in.username(), in.email(), clientIp(req));
        return Map.of("message", FORGOT_SENT);
    }

    @GetMapping("/password-reset/check")
    public Map<String, Boolean> checkReset(@RequestParam(required = false) String token, HttpServletResponse res) {
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Cache-Control", "no-store");
        return Map.of("valid", reset.check(token));
    }

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void doReset(@Valid @RequestBody ResetRequest in, HttpServletRequest req) {
        reset.reset(in.token(), in.password(), in.confirm(), clientIp(req));
    }

    /**
     * IP del client per i limiti orari. Dietro il proxy di Render il primo indirizzo di X-Forwarded-For e' il client;
     * chi lo falsifica aggira solo il limite per IP, mentre resta quello per username.
     */
    static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 45) return first;
        }
        return req.getRemoteAddr();
    }

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
