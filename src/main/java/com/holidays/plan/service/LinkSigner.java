package com.holidays.plan.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Link temporanei firmati (HMAC con la chiave JWT): permettono all'app di aprire
 * un file nel browser del telefono senza passare il token di login.
 */
@Component
public class LinkSigner {
    public static final long TTL_SECONDS = 300;
    private final SecretKey key;
    private final Clock clock;

    public LinkSigner(SecretKey jwtKey, Clock clock) {
        this.key = jwtKey;
        this.clock = clock;
    }

    public long expiry() {
        return Instant.now(clock).getEpochSecond() + TTL_SECONDS;
    }

    public String sign(String payload, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal((payload + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Firma non disponibile", e);
        }
    }

    /** Lancia 410 se scaduto, 404 se la firma non torna. */
    public void verify(String payload, long exp, String sig) {
        if (sig == null || exp < Instant.now(clock).getEpochSecond())
            throw new ApiException(org.springframework.http.HttpStatus.GONE, "Link scaduto, riaprilo dall'app");
        byte[] expected = sign(payload, exp).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII))) throw ApiException.notFound();
    }
}
