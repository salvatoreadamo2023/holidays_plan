package com.holidays.plan.web.dto;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.AbsenceType;
import com.holidays.plan.domain.RequestStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {}

    /** Input di creazione: niente id, niente utente, niente stato. Arrivano dal server. */
    public record CreateRequest(
        @NotNull AbsenceType type,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean halfDay,
        @DecimalMin("0.5") @DecimalMax("8") BigDecimal hours,
        @Size(max = 500) String note) {}

    public record Decision(@Size(max = 500) String note) {}

    public record RequestView(Long id, UserSummary user, AbsenceType type, RequestStatus status,
                              LocalDate startDate, LocalDate endDate, boolean halfDay, BigDecimal hours,
                              BigDecimal amount, String note, String decisionNote, String decidedBy,
                              Instant decidedAt, Instant createdAt) {
        public static RequestView of(AbsenceRequest r) {
            return new RequestView(r.getId(), UserSummary.of(r.getUser()), r.getType(), r.getStatus(),
                r.getStartDate(), r.getEndDate(), r.isHalfDay(), r.getHours(), r.getAmount(), r.getNote(),
                r.getDecisionNote(), r.getDecidedBy() == null ? null : r.getDecidedBy().getFullName(),
                r.getDecidedAt(), r.getCreatedAt());
        }
    }

    public record Preview(BigDecimal amount, String unit, BigDecimal availableAfter,
                          List<String> colleaguesAbsent, List<String> warnings) {}
}
