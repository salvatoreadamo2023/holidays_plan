package com.holidays.plan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidays.plan.domain.DeviceToken;
import com.holidays.plan.repo.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notifiche push tramite il servizio Expo (gratuito). Invio "best effort":
 * parte dopo il commit della transazione, in background, e un errore non blocca mai l'operazione.
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final URI EXPO = URI.create("https://exp.host/--/api/v2/push/send");

    private final DeviceTokenRepository tokens;
    private final ObjectMapper json;
    private final boolean enabled;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "push");
        t.setDaemon(true);
        return t;
    });

    public PushService(DeviceTokenRepository tokens, ObjectMapper json, @Value("${app.push.enabled:true}") boolean enabled) {
        this.tokens = tokens;
        this.json = json;
        this.enabled = enabled;
    }

    public record Message(String to, String title, String body, Map<String, Object> data) {}

    @Transactional
    public void register(Long userId, String token, String platform) {
        if (token == null || !token.matches("ExponentPushToken\\[[A-Za-z0-9_-]{10,100}]|ExpoPushToken\\[[A-Za-z0-9_-]{10,100}]"))
            throw ApiException.bad("Token non valido");
        DeviceToken d = tokens.findByToken(token).orElseGet(DeviceToken::new);
        d.setToken(token);
        d.setUserId(userId);
        d.setPlatform(platform == null ? null : platform.substring(0, Math.min(20, platform.length())));
        tokens.save(d);
    }

    @Transactional
    public void unregister(Long userId, String token) {
        tokens.findByToken(token).filter(d -> Objects.equals(d.getUserId(), userId)).ifPresent(tokens::delete);
    }

    /** Prepara le notifiche ora (dentro la transazione) e le invia dopo il commit. */
    public void notify(Collection<Long> userIds, String title, String body, Map<String, Object> data) {
        if (!enabled || userIds.isEmpty()) return;
        List<Message> msgs = tokens.findByUserIdIn(new HashSet<>(userIds)).stream()
            .map(t -> new Message(t.getToken(), title, body, data)).toList();
        if (msgs.isEmpty()) return;
        Runnable send = () -> executor.submit(() -> send(msgs));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    private void send(List<Message> msgs) {
        for (int i = 0; i < msgs.size(); i += 100) {
            List<Message> batch = msgs.subList(i, Math.min(msgs.size(), i + 100));
            try {
                List<Map<String, Object>> payload = batch.stream().map(m -> {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("to", m.to());
                    p.put("title", m.title());
                    p.put("body", m.body());
                    p.put("sound", "default");
                    p.put("channelId", "default");
                    if (m.data() != null) p.put("data", m.data());
                    return p;
                }).toList();
                HttpRequest req = HttpRequest.newBuilder(EXPO).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    log.warn("Push non inviate: HTTP {}", res.statusCode());
                    continue;
                }
                JsonNode data = json.readTree(res.body()).path("data");
                for (int k = 0; k < data.size() && k < batch.size(); k++) {
                    JsonNode t = data.get(k);
                    if ("error".equals(t.path("status").asText())
                        && "DeviceNotRegistered".equals(t.path("details").path("error").asText())) {
                        removeToken(batch.get(k).to());
                    }
                }
            } catch (Exception e) {
                log.warn("Push non inviate: {}", e.toString());
            }
        }
    }

    private void removeToken(String token) {
        try { tokens.findByToken(token).ifPresent(tokens::delete); } catch (Exception ignored) { /* non critico */ }
    }
}
