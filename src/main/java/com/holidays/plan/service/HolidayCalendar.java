package com.holidays.plan.service;

import com.holidays.plan.domain.CompanyHoliday;
import com.holidays.plan.repo.CompanyHolidayRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Map;
import java.util.TreeMap;

/** Festivita' nazionali italiane (calcolate) + chiusure aziendali (da database). */
@Service
public class HolidayCalendar {

    private static final Map<MonthDay, String> FIXED = Map.ofEntries(
        Map.entry(MonthDay.of(1, 1), "Capodanno"),
        Map.entry(MonthDay.of(1, 6), "Epifania"),
        Map.entry(MonthDay.of(4, 25), "Festa della Liberazione"),
        Map.entry(MonthDay.of(5, 1), "Festa dei Lavoratori"),
        Map.entry(MonthDay.of(6, 2), "Festa della Repubblica"),
        Map.entry(MonthDay.of(8, 15), "Ferragosto"),
        Map.entry(MonthDay.of(11, 1), "Ognissanti"),
        Map.entry(MonthDay.of(12, 8), "Immacolata"),
        Map.entry(MonthDay.of(12, 25), "Natale"),
        Map.entry(MonthDay.of(12, 26), "Santo Stefano"));

    private final CompanyHolidayRepository companyHolidays;

    public HolidayCalendar(CompanyHolidayRepository companyHolidays) {
        this.companyHolidays = companyHolidays;
    }

    /** Domenica di Pasqua (algoritmo di Meeus/Jones/Butcher). */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /** Tutte le festivita' (data -> nome) nell'intervallo, estremi inclusi. */
    public Map<LocalDate, String> holidays(LocalDate from, LocalDate to) {
        Map<LocalDate, String> out = new TreeMap<>();
        for (int y = from.getYear(); y <= to.getYear(); y++) {
            for (Map.Entry<MonthDay, String> e : FIXED.entrySet()) {
                LocalDate d = e.getKey().atYear(y);
                if (!d.isBefore(from) && !d.isAfter(to)) out.put(d, e.getValue());
            }
            LocalDate pasquetta = easter(y).plusDays(1);
            if (!pasquetta.isBefore(from) && !pasquetta.isAfter(to)) out.put(pasquetta, "Lunedi' dell'Angelo");
        }
        for (CompanyHoliday h : companyHolidays.findByDateBetweenOrderByDate(from, to)) {
            out.putIfAbsent(h.getDate(), h.getName());
        }
        return out;
    }

    public static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public boolean isWorkingDay(LocalDate d) {
        return !isWeekend(d) && !holidays(d, d).containsKey(d);
    }

    public BigDecimal workingDays(LocalDate from, LocalDate to) {
        Map<LocalDate, String> hol = holidays(from, to);
        long n = from.datesUntil(to.plusDays(1)).filter(d -> !isWeekend(d) && !hol.containsKey(d)).count();
        return BigDecimal.valueOf(n);
    }
}
