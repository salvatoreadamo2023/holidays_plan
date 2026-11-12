package com.holidays.plan.config;

import com.holidays.plan.domain.Role;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea il primo Admin solo se il database non ha ancora nessun utente
 * e se ADMIN_INITIAL_PASSWORD è impostata. Dopo il primo avvio non fa più nulla.
 */
@Component
@Order(0)
public class FirstAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FirstAdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String email;

    public FirstAdminBootstrap(UserRepository users, PasswordEncoder encoder,
                               @Value("${app.first-admin.username:admin}") String username,
                               @Value("${app.first-admin.password:}") String password,
                               @Value("${app.first-admin.email:admin@azienda.it}") String email) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank() || users.count() > 0) return;
        if (password.length() < 12) {
            log.warn("ADMIN_INITIAL_PASSWORD troppo corta (minimo 12 caratteri): primo Admin non creato");
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName("Amministratore");
        u.setPasswordHash(encoder.encode(password));
        u.setRole(Role.ADMIN);
        users.save(u);
        log.warn("Creato il primo Admin '{}'. Crea il tuo utente personale e disattiva questo.", username);
    }
}
