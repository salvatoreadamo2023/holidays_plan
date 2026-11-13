package com.holidays.plan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AbsenceType {
    /** Giorni lavorativi. */
    FERIE,
    /** Permesso a ore in un singolo giorno. */
    PERMESSO,
    /** Assenza giustificata (malattia, 104, congedi...): registrata subito, tipologia visibile solo ai responsabili. */
    GENERAL_LEAVE,
    /** CO - presso il cliente: non e' un'assenza, registrata subito e visibile a tutti. */
    CLIENT_OFFICE,
    /** BT - trasferta: non e' un'assenza, registrata subito e visibile a tutti. */
    BUSINESS_TRIP,
    /** TH - ferie ipotetiche: solo un'idea di pianificazione, non si approva e non conta nulla. Si puo' trasformare in una richiesta di ferie. */
    TENTATIVE;

    /** Non e' un'assenza (CO, BT e TH): nessuna approvazione, visibile a tutti, si toglie in autonomia, non entra nei conteggi. */
    public boolean isPresence() {
        return this == CLIENT_OFFICE || this == BUSINESS_TRIP || this == TENTATIVE;
    }

    /** Registrata subito, senza approvazione. */
    public boolean isAutoRecorded() {
        return this == GENERAL_LEAVE || isPresence();
    }

    /** Le versioni precedenti dell'app inviano ancora MALATTIA. */
    @JsonCreator
    public static AbsenceType from(String v) {
        if (v == null) return null;
        return "MALATTIA".equalsIgnoreCase(v) ? GENERAL_LEAVE : valueOf(v.toUpperCase(java.util.Locale.ROOT));
    }
}
