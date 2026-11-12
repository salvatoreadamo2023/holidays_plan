package com.holidays.plan.security;

import com.holidays.plan.config.AppProperties;
import com.holidays.plan.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.ttl = Duration.ofHours(props.jwt().ttlHours() <= 0 ? 8 : props.jwt().ttlHours());
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("holidays-plan")
            .issuedAt(now)
            .expiresAt(exp)
            .subject(String.valueOf(user.getId()))
            .claim("role", user.getRole().name())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), exp);
    }
}
