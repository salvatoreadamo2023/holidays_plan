package com.holidays.plan.service;

import com.holidays.plan.domain.Role;
import com.holidays.plan.domain.User;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Unico punto in cui si decide chi puo' fare cosa.
 * Regola d'oro: nessuno approva le proprie richieste; decide sempre un livello superiore.
 */
@Component
public class AccessPolicy {

    public boolean sameArea(User a, User b) {
        return a.areaId() != null && Objects.equals(a.areaId(), b.areaId());
    }

    /** Puo' approvare/rifiutare le richieste di {@code target}? */
    public boolean canDecide(User actor, User target) {
        if (actor == null || target == null || Objects.equals(actor.getId(), target.getId())) return false;
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case PROJECT_MANAGER -> sameArea(actor, target)
                && (target.getRole() == Role.USER || target.getRole() == Role.TEAM_LEAD);
            case TEAM_LEAD -> sameArea(actor, target) && target.getRole() == Role.USER;
            case USER -> false;
        };
    }

    /** Puo' vedere tipo, note e richieste in attesa di {@code target}? */
    public boolean canSeeDetails(User actor, User target) {
        if (Objects.equals(actor.getId(), target.getId())) return true;
        if (actor.getRole() == Role.ADMIN) return true;
        return actor.getRole().isManager() && sameArea(actor, target);
    }

    /** Puo' vedere il calendario dell'area indicata? */
    public boolean canSeeArea(User actor, Long areaId) {
        return actor.getRole() == Role.ADMIN || (areaId != null && areaId.equals(actor.areaId()));
    }
}
