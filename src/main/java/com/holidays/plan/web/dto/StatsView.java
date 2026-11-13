package com.holidays.plan.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Statistiche dell'anno scelto: per ogni tipo i 12 mesi e il totale storico.
 * Giorni lavorativi, tranne PERMESSO che e' in ore. CO e BT sono presenze, non assenze.
 */
public record StatsView(int year, LocalDate today, List<Integer> years, int people,
                        Map<String, Series> types, List<ReasonStats> reasons,
                        List<TeamStats> teams, List<OutToday> outToday) {

    public record Series(List<BigDecimal> months, BigDecimal total) {}

    public record ReasonStats(String reason, String label, Series series) {}

    public record TeamStats(Long id, String name, int people, Map<String, Series> types, List<ReasonStats> reasons) {}

    public record OutToday(Long userId, String name, String team, String type, String reason) {}
}
