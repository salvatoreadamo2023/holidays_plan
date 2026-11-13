package com.holidays.plan.service;

import com.holidays.plan.domain.AbsenceType;
import com.holidays.plan.domain.RequestStatus;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.web.dto.BalanceView;
import com.holidays.plan.web.dto.TrendView;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Il saldo non e' un campo modificabile: si ricalcola sempre dalle richieste,
 * quindi non puo' andare fuori sincrono.
 */
@Service
public class BalanceService {
    private final AbsenceRequestRepository requests;
    private final HolidayCalendar calendar;

    public BalanceService(AbsenceRequestRepository requests, HolidayCalendar calendar) {
        this.requests = requests;
        this.calendar = calendar;
    }

    /** Parte della richiesta che cade nel periodo: una ferie 28 dic - 3 gen conta per ciascun anno i suoi giorni. */
    public BigDecimal amountIn(com.holidays.plan.domain.AbsenceRequest r, LocalDate from, LocalDate to) {
        if (r.getType() == AbsenceType.PERMESSO || r.isHalfDay()) {
            return r.getStartDate().isBefore(from) || r.getStartDate().isAfter(to) ? BigDecimal.ZERO : r.getAmount();
        }
        LocalDate s = r.getStartDate().isBefore(from) ? from : r.getStartDate();
        LocalDate e = r.getEndDate().isAfter(to) ? to : r.getEndDate();
        return e.isBefore(s) ? BigDecimal.ZERO : calendar.workingDays(s, e);
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
        BigDecimal sick = sum(u, AbsenceType.GENERAL_LEAVE, active, year);
        return new BalanceView(year,
            u.getAnnualLeaveDays(), leaveUsed, leavePending,
            u.getAnnualLeaveDays().subtract(leaveUsed).subtract(leavePending),
            u.getAnnualPermitHours(), permitUsed, permitPending,
            u.getAnnualPermitHours().subtract(permitUsed).subtract(permitPending),
            sick);
    }

    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("8");

    public TrendView trend(User u, int year) {
        LocalDate from = LocalDate.of(year, 1, 1), to = LocalDate.of(year, 12, 31);
        int n = AbsenceType.values().length;
        BigDecimal[] ok = new BigDecimal[n], wait = new BigDecimal[n];
        java.util.Arrays.fill(ok, BigDecimal.ZERO);
        java.util.Arrays.fill(wait, BigDecimal.ZERO);
        for (var r : requests.findByUser(u.getId())) {
            if (r.getEndDate().isBefore(from) || r.getStartDate().isAfter(to)) continue;
            BigDecimal[] target = RequestStatus.ACTIVE.contains(r.getStatus()) ? ok
                : r.getStatus() == RequestStatus.PENDING ? wait : null;
            if (target == null) continue;
            int i = r.getType().ordinal();
            target[i] = target[i].add(amountIn(r, from, to));
        }
        int f = AbsenceType.FERIE.ordinal(), p = AbsenceType.PERMESSO.ordinal(), m = AbsenceType.GENERAL_LEAVE.ordinal();
        BigDecimal away = ok[AbsenceType.CLIENT_OFFICE.ordinal()].add(ok[AbsenceType.BUSINESS_TRIP.ordinal()]);
        BigDecimal total = ok[f].add(ok[m]).add(ok[p].divide(HOURS_PER_DAY, 1, RoundingMode.HALF_UP));
        return new TrendView(year, ok[f], wait[f], ok[p], wait[p], ok[m], total, away);
    }
}
