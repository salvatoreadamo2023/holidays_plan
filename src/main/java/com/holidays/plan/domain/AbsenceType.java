package com.holidays.plan.domain;

public enum AbsenceType {
    /** Giorni lavorativi, anche mezza giornata. Scala il saldo ferie. */
    FERIE,
    /** Permesso a ore in un singolo giorno. Scala il monte ore permessi. */
    PERMESSO,
    /** Malattia: non scala saldi, tipo visibile solo ai responsabili. */
    MALATTIA
}
