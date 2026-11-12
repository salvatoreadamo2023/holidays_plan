package com.holidays.plan.security;

import com.holidays.plan.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Blocca temporaneamente un account dopo troppi tentativi di login falliti. */
@Service
public class LoginAttemptService {

    private record Attempts(int count, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lock;
    private final Clock clock;

    public LoginAttemptService(AppProperties props) {
        this.maxAttempts = props.login() == null ? 5 : props.login().maxAttempts();
        this.lock = Duration.ofMinutes(props.login() == null ? 15 : props.login().lockMinutes());
        this.clock = Clock.systemUTC();
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(String username) {
        Attempts a = attempts.get(key(username));
        return a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(clock.instant());
    }

    public void failed(String username) {
        attempts.compute(key(username), (k, a) -> {
            int count = (a == null || (a.lockedUntil() != null && !a.lockedUntil().isAfter(clock.instant())))
                ? 1 : a.count() + 1;
            return new Attempts(count, count >= maxAttempts ? clock.instant().plus(lock) : null);
        });
    }

    public void succeeded(String username) {
        attempts.remove(key(username));
    }
}
