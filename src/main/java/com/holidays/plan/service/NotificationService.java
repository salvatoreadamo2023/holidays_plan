package com.holidays.plan.service;

import com.holidays.plan.domain.Notification;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.NotificationRepository;
import com.holidays.plan.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Un solo punto di uscita per gli avvisi: li salva per la campanella del sito,
 * li invia come push all'app e, se l'admin lo ha attivato, anche per email.
 */
@Service
public class NotificationService {
    private static final Map<String, String> SCREEN_PATH = Map.of(
        "Approvazioni", "/approvazioni", "Richieste", "/richieste", "Calendario", "/calendario");

    private final NotificationRepository repo;
    private final UserRepository users;
    private final PushService push;
    private final MailService mail;
    private final SettingsService settings;
    private final Clock clock;
    private final String baseUrl;

    public NotificationService(NotificationRepository repo, UserRepository users, PushService push, MailService mail,
                               SettingsService settings, Clock clock, @Value("${app.base-url:}") String baseUrl) {
        this.repo = repo;
        this.users = users;
        this.push = push;
        this.mail = mail;
        this.settings = settings;
        this.clock = clock;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    public record NotificationView(Long id, String title, String body, String screen, Long requestId, Instant createdAt, boolean read) {}

    public record Inbox(long unread, List<NotificationView> items) {}

    public void notify(Collection<Long> userIds, String title, String body, Map<String, Object> data) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        if (ids.isEmpty()) return;
        String screen = data == null ? null : (String) data.get("screen");
        Long requestId = data == null || data.get("requestId") == null ? null : ((Number) data.get("requestId")).longValue();
        Instant now = Instant.now(clock);
        for (Long uid : ids) {
            Notification n = new Notification();
            n.setUserId(uid);
            n.setTitle(cut(title, 200));
            n.setBody(cut(body, 1000));
            n.setScreen(screen);
            n.setRequestId(requestId);
            n.setCreatedAt(now);
            repo.save(n);
        }
        push.notify(ids, title, body, data);
        if (mail.isAvailable() && settings.emailNotifications()) {
            String link = baseUrl.isEmpty() ? null : baseUrl + SCREEN_PATH.getOrDefault(screen, "/");
            for (User u : users.findAllById(ids)) {
                if (!u.isActive()) continue;
                String text = "Ciao " + u.getFullName() + ",\n\n" + title + "\n" + (body == null ? "" : body)
                    + (link == null ? "" : "\n\nApri Gestione Ferie: " + link);
                mail.send(u.getEmail(), "[Gestione Ferie] " + title, text, link);
            }
        }
    }

    @Transactional(readOnly = true)
    public Inbox inbox(User u, int limit) {
        List<NotificationView> items = repo.findByUserIdOrderByCreatedAtDescIdDesc(u.getId(), PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
            .stream().map(n -> new NotificationView(n.getId(), n.getTitle(), n.getBody(), n.getScreen(), n.getRequestId(), n.getCreatedAt(), n.getReadAt() != null))
            .toList();
        return new Inbox(repo.countByUserIdAndReadAtIsNull(u.getId()), items);
    }

    @Transactional(readOnly = true)
    public long unread(User u) {
        return repo.countByUserIdAndReadAtIsNull(u.getId());
    }

    @Transactional
    public void markRead(User u, Long id) {
        repo.markRead(u.getId(), id, Instant.now(clock));
    }

    @Transactional
    public void markAllRead(User u) {
        repo.markAllRead(u.getId(), Instant.now(clock));
    }

    /** Pulizia: le notifiche piu' vecchie di 90 giorni vengono cancellate ogni notte. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        repo.deleteOlderThan(Instant.now(clock).minus(Duration.ofDays(90)));
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
