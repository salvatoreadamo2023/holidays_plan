package com.holidays.plan.service;

import com.holidays.plan.domain.AbsenceRequest;
import com.holidays.plan.domain.AbsenceType;
import com.holidays.plan.domain.User;
import com.holidays.plan.repo.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Testi delle notifiche e scelta dei destinatari. */
@Component
public class Notifier {
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN);
    private final PushService push;
    private final UserRepository users;
    private final AccessPolicy policy;

    public Notifier(PushService push, UserRepository users, AccessPolicy policy) {
        this.push = push;
        this.users = users;
        this.policy = policy;
    }

    /** Chi puo' decidere le richieste di {@code owner}. */
    public List<Long> approversOf(User owner) {
        return users.possibleApprovers(owner.areaId()).stream()
            .filter(u -> policy.canDecide(u, owner)).map(User::getId).toList();
    }

    static String label(AbsenceType t) {
        return switch (t) {
            case FERIE -> "Ferie";
            case PERMESSO -> "Permesso";
            case GENERAL_LEAVE -> "General leave";
            case CLIENT_OFFICE -> "Presso cliente (CO)";
            case BUSINESS_TRIP -> "Trasferta (BT)";
            case TENTATIVE -> "Ferie ipotetiche (TH)";
        };
    }

    /** Etichetta con la tipologia (solo per chi puo' vederla: interessato, responsabili, admin). */
    static String label(AbsenceRequest r) {
        return r.getLeaveReason() != null ? label(r.getType()) + " · " + r.getLeaveReason().label : label(r.getType());
    }

    static String what(AbsenceRequest r) {
        String amount = r.getAmount() == null ? "" : r.getAmount().stripTrailingZeros().toPlainString()
            + (r.getType() == AbsenceType.PERMESSO ? " h" : (r.getAmount().compareTo(BigDecimal.ONE) == 0 ? " giorno" : " giorni"));
        String when = r.getStartDate().equals(r.getEndDate()) ? D.format(r.getStartDate())
            : D.format(r.getStartDate()) + " - " + D.format(r.getEndDate());
        return label(r) + " " + amount + ", " + when;
    }

    private static Map<String, Object> data(AbsenceRequest r, String screen) {
        return Map.of("requestId", r.getId(), "screen", screen);
    }

    public void created(AbsenceRequest r) {
        push.notify(approversOf(r.getUser()), "Nuova richiesta da " + r.getUser().getFullName(), what(r), data(r, "Approvazioni"));
    }

    public void decided(AbsenceRequest r, boolean approved) {
        boolean amend = r.getAmends() != null;
        push.notify(List.of(r.getUser().getId()),
            amend ? (approved ? "Rettifica approvata ✅" : "Rettifica rifiutata") : (approved ? "Richiesta approvata ✅" : "Richiesta rifiutata"),
            what(r) + (r.getDecisionNote() != null ? " · " + r.getDecisionNote() : ""), data(r, "Richieste"));
    }

    public void cancelRequested(AbsenceRequest r) {
        push.notify(approversOf(r.getUser()), r.getUser().getFullName() + " chiede di annullare", what(r), data(r, "Approvazioni"));
    }

    public void cancelDecided(AbsenceRequest r, boolean accepted) {
        push.notify(List.of(r.getUser().getId()), accepted ? "Annullamento confermato" : "Annullamento non accettato",
            what(r), data(r, "Richieste"));
    }

    public void leaveRecorded(AbsenceRequest r) {
        push.notify(approversOf(r.getUser()), r.getUser().getFullName() + " assente", what(r), data(r, "Calendario"));
    }

    public void amendRequested(AbsenceRequest r) {
        push.notify(approversOf(r.getUser()), "Rettifica chiesta da " + r.getUser().getFullName(), what(r), data(r, "Approvazioni"));
    }

    public void amendedForYou(AbsenceRequest r, User by) {
        push.notify(List.of(r.getUser().getId()), "La tua assenza e' stata rettificata", what(r) + " · da " + by.getFullName(), data(r, "Richieste"));
    }

    public void revoked(AbsenceRequest r, User by) {
        push.notify(List.of(r.getUser().getId()), "La tua assenza e' stata annullata",
            what(r) + " · da " + by.getFullName() + (r.getDecisionNote() != null ? " · " + r.getDecisionNote() : ""), data(r, "Richieste"));
    }

    public void insertedForYou(AbsenceRequest r, User by) {
        push.notify(List.of(r.getUser().getId()), "Assenza inserita per te", what(r) + " · da " + by.getFullName(), data(r, "Richieste"));
    }

    public void certificateMissing(AbsenceRequest r) {
        push.notify(List.of(r.getUser().getId()), "Manca il certificato",
            "Allega il certificato all'assenza del " + D.format(r.getStartDate()) + ".", data(r, "Richieste"));
        List<Long> managers = approversOf(r.getUser()).stream().filter(id -> !Objects.equals(id, r.getUser().getId())).toList();
        push.notify(managers, "Certificato mancante",
            r.getUser().getFullName() + ": assenza del " + D.format(r.getStartDate()) + " ancora senza certificato.", data(r, "Approvazioni"));
    }
}
