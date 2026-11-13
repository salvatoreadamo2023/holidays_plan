package com.holidays.plan.web.dto;

import java.math.BigDecimal;

/**
 * Andamento dell'anno, calcolato dalle richieste: solo visualizzazione, nessun saldo.
 * "Approvate" = approvate o con annullamento in valutazione; "in attesa" a parte.
 * sickDays = giorni di General leave (nome storico del campo).
 * absenceDays = ferie + general leave + permessi convertiti a 8 ore per giorno.
 * awayDays = giorni presso cliente o in trasferta (non sono assenze).
 */
public record TrendView(int year,
                        BigDecimal leaveDays, BigDecimal leavePending,
                        BigDecimal permitHours, BigDecimal permitPending,
                        BigDecimal sickDays,
                        BigDecimal absenceDays,
                        BigDecimal awayDays) {}
