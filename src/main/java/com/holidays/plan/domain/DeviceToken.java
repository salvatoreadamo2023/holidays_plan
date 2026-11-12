package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_tokens")
@Getter
@Setter
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200, unique = true)
    private String token;

    @Column(length = 20)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
