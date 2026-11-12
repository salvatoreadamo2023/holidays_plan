package com.holidays.plan.web;

import com.holidays.plan.domain.User;
import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.BalanceService;
import com.holidays.plan.service.CalendarService;
import com.holidays.plan.service.RequestService;
import com.holidays.plan.web.dto.BalanceView;
import com.holidays.plan.web.dto.CalendarView;
import com.holidays.plan.web.dto.TrendView;
import com.holidays.plan.web.dto.RequestDtos.RequestView;
import com.holidays.plan.web.dto.UserSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CalendarController {
    private final CalendarService calendar;
    private final BalanceService balances;
    private final RequestService requests;
    private final CurrentUser currentUser;
    private final RequestViewer views;

    public CalendarController(CalendarService calendar, BalanceService balances, RequestService requests,
                              CurrentUser currentUser, RequestViewer views) {
        this.views = views;
        this.calendar = calendar;
        this.balances = balances;
        this.requests = requests;
        this.currentUser = currentUser;
    }

    @GetMapping("/calendar")
    @Transactional(readOnly = true)
    public CalendarView calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) Long areaId,
                                 @RequestParam(required = false) Long teamId) {
        return calendar.view(currentUser.get(), from, to, areaId, teamId);
    }

    /** Export CSV (apribile in Excel) del calendario visibile all'utente. */
    @GetMapping("/calendar/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long areaId,
                                         @RequestParam(required = false) Long teamId) {
        CalendarView v = calendar.view(currentUser.get(), from, to, areaId, teamId);
        Map<Long, CalendarView.Person> people = v.people().stream()
            .collect(Collectors.toMap(CalendarView.Person::id, Function.identity()));
        StringBuilder sb = new StringBuilder("﻿Persona;Team;Tipo;Stato;Dal;Al;Mezza giornata;Ore\n");
        for (CalendarView.Entry e : v.entries()) {
            CalendarView.Person p = people.get(e.userId());
            sb.append(csv(p.fullName())).append(';').append(csv(p.teamName())).append(';')
                .append(e.type()).append(';').append(e.status()).append(';')
                .append(e.startDate()).append(';').append(e.endDate()).append(';')
                .append(e.halfDay() ? "si" : "").append(';')
                .append(e.hours() == null ? "" : e.hours().toPlainString()).append('\n');
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"assenze_" + from + "_" + to + ".csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        // Evita formula injection in Excel
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v + "\"";
    }

    /** balance resta per le versioni vecchie dell'app; le nuove usano trend. */
    public record Dashboard(UserSummary me, BalanceView balance, TrendView trend, List<RequestView> upcoming,
                            int toApprove, List<String> absentToday) {}

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        User u = currentUser.get();
        LocalDate today = LocalDate.now(com.holidays.plan.config.ClockConfig.ZONE);
        List<RequestView> upcoming = views.many(requests.mine(u).stream()
            .filter(r -> !r.getEndDate().isBefore(today))
            .filter(r -> r.getStatus().name().matches("PENDING|APPROVED|CANCEL_REQUESTED"))
            .sorted((a, b) -> a.getStartDate().compareTo(b.getStartDate()))
            .limit(5).toList());
        return new Dashboard(UserSummary.of(u), balances.balance(u, today.getYear()),
            balances.trend(u, today.getYear()), upcoming,
            requests.toDecide(u).size(), calendar.absentToday(u));
    }

    @GetMapping("/trend")
    @Transactional(readOnly = true)
    public TrendView trend(@RequestParam(required = false) Integer year) {
        return balances.trend(currentUser.get(), year == null ? LocalDate.now(com.holidays.plan.config.ClockConfig.ZONE).getYear() : year);
    }

    @GetMapping("/balance")
    @Transactional(readOnly = true)
    public BalanceView balance(@RequestParam(required = false) Integer year) {
        return balances.balance(currentUser.get(), year == null ? LocalDate.now(com.holidays.plan.config.ClockConfig.ZONE).getYear() : year);
    }
}
