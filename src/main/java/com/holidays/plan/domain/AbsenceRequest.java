package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "absence_requests")
@Getter
@Setter
public class AbsenceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AbsenceType type;

    /** Solo per GENERAL_LEAVE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_reason", length = 30)
    private LeaveReason leaveReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Column(precision = 4, scale = 1)
    private BigDecimal hours;

    /** Giorni lavorativi oppure ore (PERMESSO). */
    @Column(nullable = false, precision = 6, scale = 1)
    private BigDecimal amount;

    @Column(length = 500)
    private String note;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "certificate_reminder_at")
    private Instant certificateReminderAt;

    @Version
    private long version;
}
