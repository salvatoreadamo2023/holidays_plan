package com.holidays.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Login login, boolean seedDemoData) {
    public record Jwt(String secret, int ttlHours) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Login(int maxAttempts, int lockMinutes) {}
}
