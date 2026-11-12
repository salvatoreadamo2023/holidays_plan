package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuditEvent(Long actorId, Long requestId, String action, String detail) {
        this.actorId = actorId;
        this.requestId = requestId;
        this.action = action;
        this.detail = detail;
    }
}
