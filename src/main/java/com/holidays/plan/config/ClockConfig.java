package com.holidays.plan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {
    /** Fuso orario aziendale: il server (Render) gira in UTC, le date vanno calcolate in Italia. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
