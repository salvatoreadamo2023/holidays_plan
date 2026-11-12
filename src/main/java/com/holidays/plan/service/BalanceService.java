package com.holidays.plan.service;

import com.holidays.plan.domain.AbsenceType;
import com.holidays.plan.domain.RequestStatus;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.web.dto.BalanceView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Il saldo non e' un campo modificabile: si ricalcola sempre dalle richieste,
 * quindi non puo' andare fuori sincrono.
 */
@Service
public class BalanceService {
    private final AbsenceRequestRepository requests;

    public BalanceService(AbsenceRequestRepository requests) {
        this.requests = requests;
    }

    private BigDecimal sum(User u, AbsenceType t, EnumSet<RequestStatus> st, int year) {
        return requests.sumAmount(u.getId(), t, st, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public BalanceView balance(User u, int year) {
        EnumSet<RequestStatus> active = EnumSet.copyOf(RequestStatus.ACTIVE);
        EnumSet<RequestStatus> pending = EnumSet.of(RequestStatus.PENDING);
        BigDecimal leaveUsed = sum(u, AbsenceType.FERIE, active, year);
        BigDecimal leavePending = sum(u, AbsenceType.FERIE, pending, year);
        BigDecimal permitUsed = sum(u, AbsenceType.PERMESSO, active, year);
        BigDecimal permitPending = sum(u, AbsenceType.PERMESSO, pending, year);
        BigDecimal sick = sum(u, AbsenceType.MALATTIA, active, year);
        return new BalanceView(year,
            u.getAnnualLeaveDays(), leaveUsed, leavePending,
            u.getAnnualLeaveDays().subtract(leaveUsed).subtract(leavePending),
            u.getAnnualPermitHours(), permitUsed, permitPending,
            u.getAnnualPermitHours().subtract(permitUsed).subtract(permitPending),
            sick);
    }
}
