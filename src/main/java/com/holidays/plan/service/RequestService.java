package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.UserRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RequestService {
    static final int MAX_PENDING = 10;
    static final int MAX_DAYS_SPAN = 60;
    static final int SICK_BACKDATE_DAYS = 30;
    static final int ON_BEHALF_BACKDATE_DAYS = 366;
    static final String AUTO_NOTE = "Approvata automaticamente: nessun responsabile disponibile";
    static final String RECORDED_NOTE = "Registrata";

    private final AbsenceRequestRepository requests;
    private final UserRepository users;
    private final HolidayCalendar calendar;
    private final AccessPolicy policy;
    private final AuditService audit;
    private final Notifier notifier;
    private final Clock clock;

    public RequestService(AbsenceRequestRepository requests, UserRepository users, HolidayCalendar calendar,
                          AccessPolicy policy, AuditService audit, Notifier notifier, Clock clock) {
        this.requests = requests;
        this.users = users;
        this.calendar = calendar;
        this.policy = policy;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    private record Computed(BigDecimal amount, String unit) {}

    /** Le TH non bloccano le richieste vere; una TH invece non si sovrappone a nulla di gia' presente. */
    private boolean overlaps(User user, CreateRequest in) {
        return in.type() == AbsenceType.TENTATIVE
            ? requests.existsOverlapForUser(user.getId(), in.startDate(), in.endDate(), RequestStatus.BLOCKING)
            : requests.existsOverlapForUserExcept(user.getId(), in.startDate(), in.endDate(), RequestStatus.BLOCKING, AbsenceType.TENTATIVE);
    }

    private static void requireReason(CreateRequest in) {
        if (in.type() == AbsenceType.GENERAL_LEAVE && in.leaveReason() == null) throw ApiException.bad("Scegli la tipologia");
    }

    private Computed validateAndCompute(User user, CreateRequest in, boolean onBehalf) {
        return validateAndCompute(user, in, onBehalf, null);
    }

    /** {@code minStart}: per le rettifiche, la data piu' vecchia ammessa (l'inizio della richiesta in corso). */
    private Computed validateAndCompute(User user, CreateRequest in, boolean onBehalf, LocalDate minStart) {
        LocalDate today = LocalDate.now(clock);
        if (in.halfDay()) throw ApiException.bad("La mezza giornata non e' piu' prevista: usa il permesso a ore");
        if (in.endDate().isBefore(in.startDate())) throw ApiException.bad("La data di fine e' prima della data di inizio");
        if (in.startDate().plusDays(MAX_DAYS_SPAN).isBefore(in.endDate()))
            throw ApiException.bad("Una singola richiesta puo' coprire al massimo " + MAX_DAYS_SPAN + " giorni");
        if (minStart != null) {
            if (in.startDate().isBefore(minStart))
                throw ApiException.bad("La rettifica non puo' iniziare prima di " + (minStart.equals(today) ? "oggi" : "l'inizio della richiesta attuale"));
        } else if (onBehalf) {
            if (in.startDate().isBefore(today.minusDays(ON_BEHALF_BACKDATE_DAYS)))
                throw ApiException.bad("Si puo' inserire un'assenza al massimo un anno indietro");
        } else if (in.type().isAutoRecorded() && in.type() != AbsenceType.TENTATIVE) {
            if (in.startDate().isBefore(today.minusDays(SICK_BACKDATE_DAYS)))
                throw ApiException.bad("Si puo' registrare al massimo " + SICK_BACKDATE_DAYS + " giorni indietro");
        } else if (in.startDate().isBefore(today)) {
            throw ApiException.bad(in.type() == AbsenceType.TENTATIVE ? "Le ferie ipotetiche si mettono solo da oggi in avanti" : "Non si possono chiedere ferie o permessi nel passato");
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
            case FERIE, GENERAL_LEAVE, CLIENT_OFFICE, BUSINESS_TRIP, TENTATIVE -> {
                BigDecimal days = calendar.workingDays(in.startDate(), in.endDate());
                if (days.signum() == 0) throw ApiException.bad("Nel periodo scelto non ci sono giorni lavorativi");
                return new Computed(days, "giorni");
            }
            default -> throw ApiException.bad("Tipo non valido");
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(User actor, CreateRequest in) {
        User user = subject(actor, in);
        boolean onBehalf = user != actor;
        Computed c = validateAndCompute(user, in, onBehalf);
        List<String> warnings = new ArrayList<>();
        if (overlaps(user, in))
            warnings.add(onBehalf ? user.getFullName() + " ha gia' un'assenza in quei giorni" : "Hai gia' una richiesta in quei giorni");
        List<String> colleagues = colleaguesAbsent(user, in.startDate(), in.endDate());
        return new Preview(c.amount(), c.unit(), null, colleagues, warnings);
    }

    private List<String> colleaguesAbsent(User user, LocalDate from, LocalDate to) {
        if (user.areaId() == null) return List.of();
        return requests.findOverlapping(from, to, RequestStatus.ACTIVE).stream()
            .filter(r -> !r.getType().isPresence())
            .map(AbsenceRequest::getUser)
            .filter(u -> !Objects.equals(u.getId(), user.getId()) && Objects.equals(u.areaId(), user.areaId()))
            .map(User::getFullName).distinct().sorted().toList();
    }

    @Transactional
    public AbsenceRequest create(User actor, CreateRequest in) {
        User user = subject(actor, in);
        if (user != actor) return createOnBehalf(actor, user, in);
        Computed c = validateAndCompute(user, in, false);
        requireReason(in);
        if (overlaps(user, in))
            throw ApiException.conflict("Hai gia' una richiesta che si sovrappone a queste date");
        long pending = in.type().isAutoRecorded() ? 0 : requests.findByUser(user.getId()).stream()
            .filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        if (pending >= MAX_PENDING)
            throw ApiException.conflict("Hai gia' " + MAX_PENDING + " richieste in attesa");

        AbsenceRequest r = new AbsenceRequest();
        r.setUser(user);
        r.setType(in.type());
        r.setStatus(RequestStatus.PENDING);
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        r.setHalfDay(false);
        r.setHours(in.type() == AbsenceType.PERMESSO ? in.hours() : null);
        r.setLeaveReason(in.type() == AbsenceType.GENERAL_LEAVE ? in.leaveReason() : null);
        r.setAmount(c.amount());
        r.setNote(blankToNull(in.note()));
        r = requests.save(r);
        audit.log(user.getId(), r.getId(), "CREATED", in.type() + " " + in.startDate() + " -> " + in.endDate());
        if (in.type().isAutoRecorded()) {
            // General leave, CO e BT non si chiedono, si comunicano: registrati subito.
            r.setStatus(RequestStatus.APPROVED);
            r.setDecidedAt(Instant.now(clock));
            r.setDecisionNote(RECORDED_NOTE);
            audit.log(null, r.getId(), "RECORDED", Notifier.label(r));
            if (in.type() == AbsenceType.GENERAL_LEAVE) notifier.leaveRecorded(r);
        } else if (notifier.approversOf(user).isEmpty()) {
            // Nessuno puo' decidere (es. unico Admin): approvazione automatica, tracciata nello storico.
            r.setStatus(RequestStatus.APPROVED);
            r.setDecidedAt(Instant.now(clock));
            r.setDecisionNote(AUTO_NOTE);
            audit.log(null, r.getId(), "AUTO_APPROVED", AUTO_NOTE);
        } else {
            notifier.created(r);
        }
        return r;
    }

    /** Per chi e' l'assenza: se stessi, oppure (solo Admin/PM con competenza) un'altra persona. */
    private User subject(User actor, CreateRequest in) {
        if (in.forUserId() == null || Objects.equals(in.forUserId(), actor.getId())) return actor;
        User target = users.findById(in.forUserId()).filter(User::isActive).orElseThrow(ApiException::notFound);
        if (!canInsertFor(actor, target)) throw ApiException.forbidden();
        return target;
    }

    private boolean canInsertFor(User actor, User target) {
        return (actor.getRole() == Role.ADMIN || actor.getRole() == Role.PROJECT_MANAGER) && policy.canDecide(actor, target);
    }

    private AbsenceRequest createOnBehalf(User actor, User user, CreateRequest in) {
        Computed c = validateAndCompute(user, in, true);
        requireReason(in);
        if (overlaps(user, in))
            throw ApiException.conflict(user.getFullName() + " ha gia' un'assenza che si sovrappone a queste date");
        AbsenceRequest r = new AbsenceRequest();
        r.setUser(user);
        r.setType(in.type());
        r.setStatus(RequestStatus.APPROVED);
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        r.setHalfDay(false);
        r.setHours(in.type() == AbsenceType.PERMESSO ? in.hours() : null);
        r.setLeaveReason(in.type() == AbsenceType.GENERAL_LEAVE ? in.leaveReason() : null);
        r.setAmount(c.amount());
        r.setNote(blankToNull(in.note()));
        r.setDecidedBy(actor);
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote("Inserita da " + actor.getFullName());
        r = requests.save(r);
        audit.log(actor.getId(), r.getId(), "CREATED_ON_BEHALF",
            user.getFullName() + ": " + in.type() + " " + in.startDate() + " -> " + in.endDate());
        notifier.insertedForYou(r, actor);
        return r;
    }

    /** Persone per cui questo utente puo' inserire un'assenza (vuoto per chi non puo'). */
    @Transactional(readOnly = true)
    public List<User> insertableFor(User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.PROJECT_MANAGER) return List.of();
        return users.findByActiveTrueOrderByFullName().stream().filter(u -> canInsertFor(actor, u)).toList();
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
        applyAmendment(r, actor, blankToNull(note));
        audit.log(actor.getId(), r.getId(), "APPROVED", note);
        notifier.decided(r, true);
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
        notifier.decided(r, false);
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
        if (r.getType().isPresence()) {
            // CO, BT e TH si tolgono in autonomia, come si mettono.
            r.setStatus(RequestStatus.CANCELLED);
            r.setDecidedAt(Instant.now(clock));
            audit.log(actor.getId(), r.getId(), "CANCELLED", "Tolta dall'interessato");
            return r;
        }
        audit.log(actor.getId(), r.getId(), "CANCEL_REQUESTED", note);
        if (notifier.approversOf(r.getUser()).isEmpty()) {
            r.setStatus(RequestStatus.CANCELLED);
            r.setDecidedAt(Instant.now(clock));
            audit.log(null, r.getId(), "AUTO_CANCELLED", AUTO_NOTE);
        } else {
            r.setStatus(RequestStatus.CANCEL_REQUESTED);
            notifier.cancelRequested(r);
        }
        return r;
    }

    /** Trasforma una TH (ferie ipotetiche) in una vera richiesta di ferie, con le stesse date: la TH sparisce. */
    @Transactional
    public AbsenceRequest convertTentative(User actor, Long id) {
        AbsenceRequest th = load(id);
        if (!Objects.equals(th.getUser().getId(), actor.getId())) throw ApiException.forbidden();
        if (th.getType() != AbsenceType.TENTATIVE || th.getStatus() != RequestStatus.APPROVED)
            throw ApiException.conflict("Si possono trasformare solo ferie ipotetiche (TH) attive");
        th.setStatus(RequestStatus.CANCELLED);
        th.setDecidedAt(Instant.now(clock));
        requests.saveAndFlush(th);
        audit.log(actor.getId(), th.getId(), "CONVERTED", "TH trasformata in richiesta di ferie");
        return create(actor, new CreateRequest(AbsenceType.FERIE, th.getStartDate(), th.getEndDate(), false, null, th.getNote(), null, null));
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
        notifier.cancelDecided(r, accept);
        return r;
    }

    // ---------------------------------------------------------------- Rettifiche

    /**
     * Rettifica di un'assenza approvata, di oggi o futura (o in corso).
     * Chi ha l'assenza la chiede: e' una nuova richiesta che, se approvata, sostituisce l'originale (se rifiutata resta tutto com'e').
     * Un responsabile con competenza (Admin compreso) la rettifica subito, e la persona riceve una notifica.
     */
    @Transactional
    public AbsenceRequest amend(User actor, Long id, CreateRequest in) {
        AbsenceRequest orig = load(id);
        User owner = orig.getUser();
        boolean self = Objects.equals(owner.getId(), actor.getId());
        if (!self && !policy.canDecide(actor, owner)) throw ApiException.forbidden();
        if (!RequestStatus.ACTIVE.contains(orig.getStatus()))
            throw ApiException.conflict("Si possono rettificare solo richieste approvate");
        LocalDate today = LocalDate.now(clock);
        if (orig.getEndDate().isBefore(today)) throw ApiException.conflict("Il periodo e' gia' trascorso: non si puo' rettificare");
        if (orig.getType() == AbsenceType.TENTATIVE)
            throw ApiException.conflict("Le ferie ipotetiche (TH) si tolgono o si trasformano, non si rettificano");
        if (in.type() == AbsenceType.TENTATIVE) throw ApiException.bad("Una rettifica non puo' diventare ferie ipotetiche (TH)");
        List<AbsenceRequest> pendingAmendments = pendingAmendmentsOf(orig);
        if (self && !pendingAmendments.isEmpty())
            throw ApiException.conflict("C'e' gia' una rettifica in attesa per questa richiesta");

        LocalDate minStart = orig.getStartDate().isBefore(today) ? orig.getStartDate() : today;
        Computed c = validateAndCompute(owner, in, false, minStart);
        requireReason(in);
        if (sameAs(orig, in)) throw ApiException.bad("La rettifica e' uguale alla richiesta attuale");
        Set<Long> skip = new HashSet<>();
        skip.add(orig.getId());
        pendingAmendments.forEach(a -> skip.add(a.getId()));
        if (overlapsExcept(owner, in, skip))
            throw ApiException.conflict(self ? "Hai gia' un'altra richiesta che si sovrappone a queste date"
                : owner.getFullName() + " ha gia' un'altra assenza che si sovrappone a queste date");
        if (self) {
            long pending = requests.findByUser(owner.getId()).stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
            if (pending >= MAX_PENDING) throw ApiException.conflict("Hai gia' " + MAX_PENDING + " richieste in attesa");
        }

        AbsenceRequest r = new AbsenceRequest();
        r.setUser(owner);
        r.setType(in.type());
        r.setStatus(RequestStatus.PENDING);
        r.setStartDate(in.startDate());
        r.setEndDate(in.endDate());
        r.setHalfDay(false);
        r.setHours(in.type() == AbsenceType.PERMESSO ? in.hours() : null);
        r.setLeaveReason(in.type() == AbsenceType.GENERAL_LEAVE ? in.leaveReason() : null);
        r.setAmount(c.amount());
        r.setNote(blankToNull(in.note()));
        r.setAmends(orig);
        r = requests.save(r);
        String what = "Rettifica di #" + orig.getId() + ": " + in.type() + " " + in.startDate() + " -> " + in.endDate();

        if (!self) {
            audit.log(actor.getId(), r.getId(), "AMENDED_BY_MANAGER", owner.getFullName() + " · " + what);
            applyAmendment(r, actor, "Rettificata da " + actor.getFullName());
            for (AbsenceRequest a : pendingAmendments) {
                a.setStatus(RequestStatus.REJECTED);
                a.setDecidedBy(actor);
                a.setDecidedAt(Instant.now(clock));
                a.setDecisionNote("Superata dalla rettifica di " + actor.getFullName());
                audit.log(actor.getId(), a.getId(), "REJECTED", a.getDecisionNote());
            }
            notifier.amendedForYou(r, actor);
            return r;
        }
        audit.log(owner.getId(), r.getId(), "AMEND_REQUESTED", what);
        if (orig.getType().isPresence() && in.type().isAutoRecorded()) {
            // CO, BT si tolgono e si mettono in autonomia: la rettifica vale subito.
            applyAmendment(r, null, RECORDED_NOTE);
            audit.log(null, r.getId(), "RECORDED", Notifier.label(r));
            if (in.type() == AbsenceType.GENERAL_LEAVE) notifier.leaveRecorded(r);
        } else if (notifier.approversOf(owner).isEmpty()) {
            applyAmendment(r, null, AUTO_NOTE);
            audit.log(null, r.getId(), "AUTO_APPROVED", AUTO_NOTE);
        } else {
            notifier.amendRequested(r);
        }
        return r;
    }

    /** Approva la richiesta; se e' una rettifica, l'originale ancora attiva viene sostituita (annullata). */
    private void applyAmendment(AbsenceRequest r, User by, String note) {
        Instant now = Instant.now(clock);
        r.setStatus(RequestStatus.APPROVED);
        r.setDecidedBy(by);
        r.setDecidedAt(now);
        r.setDecisionNote(note);
        AbsenceRequest o = r.getAmends();
        if (o != null && RequestStatus.ACTIVE.contains(o.getStatus())) {
            o.setStatus(RequestStatus.CANCELLED);
            o.setDecidedAt(now);
            o.setDecisionNote("Sostituita dalla rettifica");
            audit.log(by == null ? null : by.getId(), o.getId(), "AMENDED", "Sostituita da #" + r.getId());
        }
    }

    private List<AbsenceRequest> pendingAmendmentsOf(AbsenceRequest orig) {
        return requests.findByUser(orig.getUser().getId()).stream()
            .filter(r -> r.getStatus() == RequestStatus.PENDING && r.getAmends() != null
                && Objects.equals(r.getAmends().getId(), orig.getId()))
            .toList();
    }

    private static boolean sameAs(AbsenceRequest o, CreateRequest in) {
        return o.getType() == in.type() && o.getStartDate().equals(in.startDate()) && o.getEndDate().equals(in.endDate())
            && (in.type() != AbsenceType.PERMESSO || (o.getHours() != null && in.hours() != null && o.getHours().compareTo(in.hours()) == 0))
            && (in.type() != AbsenceType.GENERAL_LEAVE || o.getLeaveReason() == in.leaveReason());
    }

    private boolean overlapsExcept(User user, CreateRequest in, Set<Long> skip) {
        return requests.findByUser(user.getId()).stream()
            .filter(r -> !skip.contains(r.getId()) && RequestStatus.BLOCKING.contains(r.getStatus()) && r.getType() != AbsenceType.TENTATIVE)
            .anyMatch(r -> !r.getStartDate().isAfter(in.endDate()) && !r.getEndDate().isBefore(in.startDate()));
    }

    /** Un responsabile con competenza (Admin compreso) annulla subito un'assenza approvata di oggi o futura, con un motivo. */
    @Transactional
    public AbsenceRequest revoke(User actor, Long id, String note) {
        AbsenceRequest r = load(id);
        if (!policy.canDecide(actor, r.getUser())) throw ApiException.forbidden();
        if (!RequestStatus.ACTIVE.contains(r.getStatus())) throw ApiException.conflict("Si possono annullare solo richieste approvate");
        if (r.getEndDate().isBefore(LocalDate.now(clock))) throw ApiException.conflict("Il periodo e' gia' trascorso");
        if (blankToNull(note) == null) throw ApiException.bad("Indica il motivo dell'annullamento");
        r.setStatus(RequestStatus.CANCELLED);
        r.setDecidedBy(actor);
        r.setDecidedAt(Instant.now(clock));
        r.setDecisionNote(note.trim());
        audit.log(actor.getId(), r.getId(), "REVOKED", note.trim());
        for (AbsenceRequest a : pendingAmendmentsOf(r)) {
            a.setStatus(RequestStatus.REJECTED);
            a.setDecidedBy(actor);
            a.setDecidedAt(Instant.now(clock));
            a.setDecisionNote("La richiesta originale e' stata annullata");
            audit.log(actor.getId(), a.getId(), "REJECTED", a.getDecisionNote());
        }
        notifier.revoked(r, actor);
        return r;
    }

    @Transactional(readOnly = true)
    public List<AbsenceRequest> mine(User user) {
        return requests.findByUser(user.getId());
    }

    /** Assenze approvate, in corso o future, delle persone di competenza: il responsabile le puo' rettificare o annullare. */
    @Transactional(readOnly = true)
    public List<AbsenceRequest> teamActive(User actor) {
        if (!actor.getRole().isManager()) return List.of();
        LocalDate today = LocalDate.now(clock);
        return requests.findOverlapping(today, today.plusYears(2), java.util.EnumSet.of(RequestStatus.APPROVED)).stream()
            .filter(r -> r.getType() != AbsenceType.TENTATIVE && policy.canDecide(actor, r.getUser()))
            .sorted(java.util.Comparator.comparing(AbsenceRequest::getStartDate))
            .toList();
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
