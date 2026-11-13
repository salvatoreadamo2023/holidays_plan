package com.holidays.plan.domain;

/** Tipologie del General leave. */
public enum LeaveReason {
    MALATTIA("Malattia", true),
    MALATTIA_FIGLIO("Malattia figlio", true),
    VISITA_MEDICA("Visita medica", false),
    LEGGE_104("Legge 104", false),
    MATERNITA_PATERNITA("Maternità / paternità", false),
    CONGEDO_PARENTALE("Congedo parentale", false),
    CONGEDO_MATRIMONIALE("Congedo matrimoniale", false),
    LUTTO("Lutto", false),
    DONAZIONE_SANGUE("Donazione sangue", false),
    SEGGIO_ELETTORALE("Seggio elettorale", false),
    STUDIO_ESAMI("Studio / esami", false),
    ALTRO("Altro", false);

    public final String label;
    /** Serve un certificato: dopo qualche giorno senza allegato parte un promemoria. */
    public final boolean needsCertificate;

    LeaveReason(String label, boolean needsCertificate) {
        this.label = label;
        this.needsCertificate = needsCertificate;
    }
}
