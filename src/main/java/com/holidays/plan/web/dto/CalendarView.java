package com.holidays.plan.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CalendarView(LocalDate from, LocalDate to, List<Day> days, List<Person> people, List<Entry> entries,
                           List<Option> areas, List<Option> teams, Long areaId, Long teamId) {

    public record Day(LocalDate date, boolean weekend, String holiday) {}

    public record Person(Long id, String fullName, String role, Long teamId, String teamName) {}

    /** type e' "ASSENZA" quando chi guarda non puo' vedere il dettaglio. */
    public record Entry(Long requestId, Long userId, LocalDate startDate, LocalDate endDate, String type,
                        String status, boolean halfDay, BigDecimal hours, String reason) {}

    public record Option(Long id, String name) {}
}
