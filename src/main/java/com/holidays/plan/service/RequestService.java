package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.web.dto.RequestDtos.CreateRequest;
import com.holidays.plan.web.dto.RequestDtos.Preview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Service
public class RequestService {
    static final int MAX_PENDING = 10;
    static final int MAX_DAYS_SPAN = 60;
    static final int SICK_BACKDATE_DAYS = 30;

    private final AbsenceRequestRepository requests;
    private final HolidayCalendar calendar;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    public RequestService(AbsenceRequestRepository requests, HolidayCalendar calendar, 
                          AccessPolicy policy, AuditService audit, Clock clock) {
        this.requests = requests;
        this.calendar = calendar;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    private record Computed(BigDecimal amount, String unit) {}

    private Computed validateAndCompute(User user, CreateRequest in) {
        LocalDate today = LocalDate.now(clock);
        if (in.endDate().isBefore(in.startDate())) throw ApiException.bad("La data di fine e' prima della data di inizio");
        if (in.startDate().plusDays(MAX_DAYS_SPAN).isBefore(in.endDate()))
            throw ApiException.bad("Una singola richiesta puo' coprire al massimo " + MAX_DAYS_SPAN + " giorni");
        if (in.type() == AbsenceType.MALATTIA) {
            if (in.startDate().isBefore(today.minusDays(SICK_BACKDATE_DAYS)))
                throw ApiException.bad("La malattia si puo' registrare al massimo " + SICK_BACKDATE_DAYS + " giorni indietro");
        } else if (in.startDate().isBefore(today)) {
            throw ApiException.bad("Non si possono chiedere ferie o permessi nel passato");
        }
        if (in.startDate().isAfter(today.plusYears(1).withMonth(12).withDayOfMonth(31)))
            throw ApiException.bad("Data troppo lontana nel futuro");

        switch (in.type()) {
            case PERMESSO -> {
                if (!in.startDate().equals(in.endDate())) throw ApiException.bad("Il permesso a ore vale per un solo giorno");
                if (in.hours() == null) throw ApiException.bad("Indica quante ore di permesso");
                if (in.hours().remainder(new BigDecimal("0.5")).signum() != 0)
                    throw ApiException.bad("Le ore vanno indicate a multipli di mezz'ora");
                if (!calendar.isWorkingDay(in.startDate())) throw ApiException.bad("Il giorno scelto non e' lavorativo");
                return new Computed(in.hours(), "ore");
            }
            case FERIE, MALATTIA -> {
                if (in.halfDay()) {
                    if (in.type() != AbsenceType.FERIE) throw ApiException.bad("La mezza giornata vale solo per le ferie");
                    if (!in.startDate().equals(in.endDate())) throw ApiException.bad("La mezza giornata vale per un solo giorno");
                    if (!calendar.isWorkingDay(in.startDate())) throw ApiException.bad("Il giorno scelto non e' lavorativo");
                    return new Computed(new BigDecimal("0.5"), "giorni");
                }
                BigDecimal days = calendar.workingDays(in.startDate(), in.endDate());
                if (days.signum() == 0) throw ApiException.bad("Nel periodo scelto non ci sono giorni lavorativi");
                return new Computed(days, "giorni");
            }
            default -> throw ApiException.bad("Tipo non valido");
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(User user, CreateRequest in) {
        Computed c = validateAndCompute(user, in);
        List<String> warnings = new ArrayList<>();
        if (requests.existsOverlapForUser(user.getId(), in.startDate(), in.endDate(), RequestStatus.BLOCKING))
            warnings.add("Hai gia' una richiesta in quei giorni");
        List<String> colleagues = colleaguesAbsent(user, in.startDate(), in.endDate());
        return new Preview(c.amount(), c.unit(), null, colleagues, warnings);
    }

    private List<String> colleaguesAbsent(User user, LocalDate from, LocalDate to) {
        if (user.areaId() == null) return List.of();
        return requests.findOverlapping(from, to, RequestStatus.ACTIVE).stream()
            .map(AbsenceRequest::getUser)
            .filter(u -> !Objects.equals(u.getId(), user.getId()) && Objects.equals(u.areaId(), user.areaId()))
            .map(User::getFullName).distinct().sorted().toList();
    }

    @Transactional
    public AbsenceRequest create(User user, CreateRequest in) {
        Computed c = validateAndCompute(user, in);
        if (requests.existsOverlapForUser(user.getId(), in.startDate(), in.endDate(), RequestStatus.BLOCKING))
            throw ApiException.conflict("Hai gia' una richiesta che si sovrappone a queste date");
        long pending = requests.findByUser(user.getId()).stream()
            .filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        if (pending >= MAX_PENDING)
            throw ApiException.conflict("Hai gia' " + MAX_PENDING + " richieste in attesa");

        AbsenceRequest r = new AbsenceRequest();
        r.setUser(user);
        r.setType(in.type());
        r.setStatus(RequestStatus.PENDING);
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        r.setHalfDay(in.type() == AbsenceType.FERIE && in.halfDay());
        r.setHours(in.type() == AbsenceType.PERMESSO ? in.hours() : null);
        r.setAmount(c.amount());
        r.setNote(blankToNull(in.note()));
        r = requests.save(r);
        audit.log(user.getId(), r.getId(), "CREATED", in.type() + " " + in.startDate() + " -> " + in.endDate());
        return r;
    }

    private AbsenceRequest load(Long id) {
        return requests.findById(id).orElseThrow(ApiException::notFound);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional
    public AbsenceRequest approve(User actor, Long id, String note) {
        AbsenceRequest r = load(id);
        if (!policy.canDecide(actor, r.getUser())) throw ApiException.forbidden();
        if (r.getStatus() != RequestStatus.PENDING) throw ApiException.conflict("La richiesta non e' piu' in attesa");
        r.setStatus(RequestStatus.APPROVED);
        r.setDecidedBy(actor);
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote(blankToNull(note));
        audit.log(actor.getId(), r.getId(), "APPROVED", note);
        return r;
    }

    @Transactional
    public AbsenceRequest reject(User actor, Long id, String note) {
        AbsenceRequest r = load(id);
        if (!policy.canDecide(actor, r.getUser())) throw ApiException.forbidden();
        if (r.getStatus() != RequestStatus.PENDING) throw ApiException.conflict("La richiesta non e' piu' in attesa");
        if (blankToNull(note) == null) throw ApiException.bad("Indica il motivo del rifiuto");
        r.setStatus(RequestStatus.REJECTED);
        r.setDecidedBy(actor);
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote(note.trim());
        audit.log(actor.getId(), r.getId(), "REJECTED", note);
        return r;
    }

    /** L'utente ritira una propria richiesta ancora in attesa. */
    @Transactional
    public AbsenceRequest withdraw(User actor, Long id) {
        AbsenceRequest r = load(id);
        if (!Objects.equals(r.getUser().getId(), actor.getId())) throw ApiException.forbidden();
        if (r.getStatus() != RequestStatus.PENDING) throw ApiException.conflict("Si possono ritirare solo richieste in attesa");
        r.setStatus(RequestStatus.WITHDRAWN);
        audit.log(actor.getId(), r.getId(), "WITHDRAWN", null);
        return r;
    }

    /** L'utente chiede di annullare una richiesta gia' approvata: decide il responsabile. */
    @Transactional
    public AbsenceRequest requestCancel(User actor, Long id, String note) {
        AbsenceRequest r = load(id);
        if (!Objects.equals(r.getUser().getId(), actor.getId())) throw ApiException.forbidden();
        if (r.getStatus() != RequestStatus.APPROVED) throw ApiException.conflict("Si possono annullare solo richieste approvate");
        if (r.getEndDate().isBefore(LocalDate.now(clock))) throw ApiException.conflict("Il periodo e' gia' trascorso");
        r.setStatus(RequestStatus.CANCEL_REQUESTED);
        audit.log(actor.getId(), r.getId(), "CANCEL_REQUESTED", note);
        return r;
    }

    @Transactional
    public AbsenceRequest decideCancel(User actor, Long id, boolean accept, String note) {
        AbsenceRequest r = load(id);
        if (!policy.canDecide(actor, r.getUser())) throw ApiException.forbidden();
        if (r.getStatus() != RequestStatus.CANCEL_REQUESTED) throw ApiException.conflict("Nessun annullamento da valutare");
        r.setStatus(accept ? RequestStatus.CANCELLED : RequestStatus.APPROVED);
        r.setDecidedBy(actor);
        r.setDecidedAt(Instant.now(clock));
        if (blankToNull(note) != null) r.setDecisionNote(note.trim());
        audit.log(actor.getId(), r.getId(), accept ? "CANCELLED" : "CANCEL_DENIED", note);
        return r;
    }

    @Transactional(readOnly = true)
    public List<AbsenceRequest> mine(User user) {
        return requests.findByUser(user.getId());
    }

    /** Richieste che questo utente puo' decidere adesso. */
    @Transactional(readOnly = true)
    public List<AbsenceRequest> toDecide(User actor) {
        if (!actor.getRole().isManager()) return List.of();
        return requests.findByStatusIn(EnumSet.of(RequestStatus.PENDING, RequestStatus.CANCEL_REQUESTED)).stream()
            .filter(r -> policy.canDecide(actor, r.getUser()))
            .toList();
    }
}
