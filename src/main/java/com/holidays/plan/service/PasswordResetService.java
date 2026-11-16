package com.holidays.plan.service;

import com.holidays.plan.domain.PasswordResetToken;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.PasswordResetTokenRepository;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.security.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reset della password con link via email, sullo stesso schema di Digisol:
 * token casuale a 256 bit, nel DB solo lo SHA-256, valido 30 minuti, monouso,
 * risposta sempre identica (non rivela se username o email esistono), limiti per ora.
 */
@Service
public class PasswordResetService {
    public static final int TOKEN_MINUTES = 30;
    private static final int MAX_PER_USERNAME = 3, MAX_PER_IP = 10, MAX_SET_PER_IP = 20;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Rome"));

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final AuditService audit;
    private final LoginAttemptService attempts;
    private final Clock clock;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                MailService mail, AuditService audit, LoginAttemptService attempts, Clock clock,
                                @Value("${app.base-url:}") String baseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.audit = audit;
        this.attempts = attempts;
        this.clock = clock;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    /** La funzione e' attiva solo con email configurata e indirizzo pubblico del sito valido. */
    public boolean isAvailable() {
        if (!mail.isAvailable() || baseUrl.isEmpty()) return false;
        try {
            URI u = URI.create(baseUrl);
            return ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) && u.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Sempre la stessa risposta al chiamante: l'esito vero finisce solo nell'audit. */
    @Transactional
    public void requestLink(String username, String email, String ip) {
        if (!isAvailable()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Il reset della password non e' disponibile: contatta un amministratore");
        String un = username == null ? "" : username.trim();
        String em = email == null ? "" : email.trim();
        if (un.isEmpty() || em.isEmpty()) throw ApiException.bad("Scrivi username ed email");
        if (!allow("u:" + un.toLowerCase(Locale.ROOT), MAX_PER_USERNAME) | !allow("ip:" + ip, MAX_PER_IP)) {
            audit.log(null, null, "PASSWORD_RESET_LIMIT", "Troppe richieste di reset per " + cut(un, 60));
            return;
        }
        User u = users.findByUsernameIgnoreCase(un).orElse(null);
        if (u == null || !u.isActive()) {
            audit.log(null, null, "PASSWORD_RESET_REQUESTED", "Utente inesistente o disattivato (" + cut(un, 60) + "): nessuna email");
            return;
        }
        if (!u.getEmail().trim().equalsIgnoreCase(em)) {
            audit.log(u.getId(), null, "PASSWORD_RESET_REQUESTED", "Email non corrispondente: nessuna email");
            return;
        }
        Instant now = Instant.now(clock);
        tokens.deleteExpired(now.minus(Duration.ofDays(1)));
        tokens.invalidateAll(u.getId(), now);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(u.getId());
        t.setTokenHash(sha256(token));
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(Duration.ofMinutes(TOKEN_MINUTES)));
        t.setIp(cut(ip, 45));
        tokens.save(t);
        String link = baseUrl + "/password-reset?token=" + token;
        mail.send(u.getEmail(), "[Gestione Ferie] Reimposta la password",
            "Ciao " + u.getFullName() + ",\n\n"
                + "abbiamo ricevuto una richiesta di reimpostazione della password per l'utente " + u.getUsername() + ".\n"
                + "Per scegliere una nuova password apri questo link entro " + TOKEN_MINUTES + " minuti:\n\n"
                + link + "\n\n"
                + "Il link si puo' usare una sola volta. Se non hai chiesto tu il reset, ignora questa email: la password attuale resta valida.",
            link);
        audit.log(u.getId(), null, "PASSWORD_RESET_REQUESTED", "Link di reset inviato per email");
    }

    /** Controlla il link senza consumarlo (gli scanner delle email aprono i link). */
    @Transactional(readOnly = true)
    public boolean check(String token) {
        return valid(token).isPresent();
    }

    @Transactional
    public void reset(String token, String password, String confirm, String ip) {
        if (!allow("set:" + ip, MAX_SET_PER_IP)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Troppi tentativi: riprova tra un'ora");
        PasswordResetToken t = valid(token).orElseThrow(() -> ApiException.bad("Il link non e' valido, e' scaduto oppure e' gia' stato usato. Puoi chiederne uno nuovo."));
        User u = users.findById(t.getUserId()).filter(User::isActive)
            .orElseThrow(() -> ApiException.bad("Il link non e' valido, e' scaduto oppure e' gia' stato usato. Puoi chiederne uno nuovo."));
        if (password == null || password.isEmpty()) throw ApiException.bad("Scrivi la nuova password");
        if (!password.equals(confirm)) throw ApiException.bad("Le due password non coincidono");
        if (password.length() < 10) throw ApiException.bad("La password deve avere almeno 10 caratteri");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.bad("La password e' troppo lunga");
        if (!password.equals(password.strip())) throw ApiException.bad("La password non puo' iniziare o finire con uno spazio");
        if (password.equalsIgnoreCase(u.getUsername())) throw ApiException.bad("La password non puo' essere uguale allo username");
        if (encoder.matches(password, u.getPasswordHash())) throw ApiException.bad("La nuova password deve essere diversa da quella attuale");
        Instant now = Instant.now(clock);
        if (tokens.consume(t.getTokenHash(), now) != 1)
            throw ApiException.bad("Il link non e' valido, e' scaduto oppure e' gia' stato usato. Puoi chiederne uno nuovo.");
        tokens.invalidateAll(u.getId(), now);
        u.setPasswordHash(encoder.encode(password));
        u.setMustChangePassword(false);
        u.setPasswordChangedAt(now);
        users.save(u);
        attempts.succeeded(u.getUsername());
        audit.log(u.getId(), null, "PASSWORD_RESET_DONE", "Password reimpostata con il link via email");
        mail.send(u.getEmail(), "[Gestione Ferie] Password modificata",
            "Ciao " + u.getFullName() + ",\n\n"
                + "la password dell'utente " + u.getUsername() + " e' stata modificata il " + WHEN.format(now) + ".\n"
                + "Tutte le sessioni aperte sono state chiuse.\n\n"
                + "Se non sei stato tu, contatta subito un amministratore.", null);
    }

    private Optional<PasswordResetToken> valid(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        Instant now = Instant.now(clock);
        return tokens.findByTokenHash(sha256(token)).filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
    }

    private boolean allow(String key, int max) {
        Instant now = Instant.now(clock), cutoff = now.minus(Duration.ofHours(1));
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty(); } });
        return true;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}
