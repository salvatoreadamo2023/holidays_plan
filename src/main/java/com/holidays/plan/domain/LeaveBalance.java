package com.holidays.plan.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Ultimo saldo residuo ufficiale di una persona, come da Excel caricato dall'admin. */
@Entity
@Table(name = "leave_balances")
@Getter
@Setter
public class LeaveBalance {
    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Primo giorno del mese a cui si riferisce il saldo. */
    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "leave_days", precision = 6, scale = 2)
    private BigDecimal leaveDays;

    @Column(name = "permit_hours", precision = 7, scale = 2)
    private BigDecimal permitHours;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by")
    private Long uploadedBy;
}
