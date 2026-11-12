package com.holidays.plan.web.dto;

import java.math.BigDecimal;

/**
 * Andamento dell'anno, calcolato dalle richieste: solo visualizzazione, nessun saldo.
 * "Approvate" = approvate o con annullamento in valutazione; "in attesa" a parte.
 * absenceDays = ferie + malattia + permessi convertiti a 8 ore per giorno.
 */
public record TrendView(int year,
                        BigDecimal leaveDays, BigDecimal leavePending,
                        BigDecimal permitHours, BigDecimal permitPending,
                        BigDecimal sickDays,
                        BigDecimal absenceDays) {}
