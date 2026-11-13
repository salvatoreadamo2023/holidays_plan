package com.holidays.plan.service;

import com.holidays.plan.config.ClockConfig;
import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.TeamRepository;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.web.dto.StatsView;
import com.holidays.plan.web.dto.StatsView.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/**
 * Statistiche per l'Admin, calcolate dalle richieste approvate (anche con annullamento in valutazione).
 * Ogni richiesta viene spezzata giorno per giorno: contano solo i giorni lavorativi (niente weekend e festivita'),
 * e una richiesta a cavallo di due mesi o due anni viene divisa tra i due. I permessi contano in ore nel loro giorno.
 */
@Service
public class StatsService {
    private final AbsenceRequestRepository requests;
    private final UserRepository users;
    private final TeamRepository teams;
    private final HolidayCalendar calendar;
    private final Clock clock;

    public StatsService(AbsenceRequestRepository requests, UserRepository users, TeamRepository teams,
                        HolidayCalendar calendar, Clock clock) {
        this.requests = requests;
        this.users = users;
        this.teams = teams;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** Somma per mese dell'anno scelto (12 valori) piu' il totale storico. */
    private static final class Acc {
        final BigDecimal[] months = zeros(12);
        BigDecimal total = BigDecimal.ZERO;

        void add(LocalDate d, int year, BigDecimal v) {
            total = total.add(v);
            if (d.getYear() == year) months[d.getMonthValue() - 1] = months[d.getMonthValue() - 1].add(v);
        }

        Series view() {
            return new Series(Arrays.asList(months), total);
        }
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static Map<AbsenceType, Acc> accs() {
        Map<AbsenceType, Acc> m = new EnumMap<>(AbsenceType.class);
        for (AbsenceType t : AbsenceType.values()) m.put(t, new Acc());
        return m;
    }

    private static Map<String, Series> views(Map<AbsenceType, Acc> m) {
        Map<String, Series> out = new LinkedHashMap<>();
        m.forEach((t, a) -> out.put(t.name(), a.view()));
        return out;
    }

    @Transactional(readOnly = true)
    public StatsView stats(int year, LeaveReason reason) {
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.ZONE));
        List<AbsenceRequest> list = requests.findByStatusIn(RequestStatus.ACTIVE).stream()
            .filter(r -> reason == null || r.getType() != AbsenceType.GENERAL_LEAVE || r.getLeaveReason() == reason)
            .toList();

        Map<LocalDate, String> hol = list.isEmpty() ? Map.of() : calendar.holidays(
            list.stream().map(AbsenceRequest::getStartDate).min(Comparator.naturalOrder()).get(),
            list.stream().map(AbsenceRequest::getEndDate).max(Comparator.naturalOrder()).get());

        List<User> active = users.findByActiveTrueOrderByFullName();
        Map<Long, Integer> headcount = new HashMap<>();
        active.forEach(u -> headcount.merge(teamKey(u), 1, Integer::sum));

        Map<AbsenceType, Acc> all = accs();
        Map<Long, Map<AbsenceType, Acc>> byTeam = new HashMap<>();
        Map<LeaveReason, Acc> byReason = new EnumMap<>(LeaveReason.class);
        Map<Long, Map<LeaveReason, Acc>> byTeamReason = new HashMap<>();
        SortedSet<Integer> years = new TreeSet<>(List.of(year, today.getYear()));
        List<OutToday> out = new ArrayList<>();

        for (AbsenceRequest r : list) {
            Map<AbsenceType, Acc> team = byTeam.computeIfAbsent(teamKey(r.getUser()), k -> accs());
            Acc reasonAcc = r.getLeaveReason() == null ? null : byReason.computeIfAbsent(r.getLeaveReason(), k -> new Acc());
            Acc teamReasonAcc = r.getLeaveReason() == null ? null : byTeamReason
                .computeIfAbsent(teamKey(r.getUser()), k -> new EnumMap<>(LeaveReason.class))
                .computeIfAbsent(r.getLeaveReason(), k -> new Acc());
            if (r.getType() == AbsenceType.PERMESSO || r.isHalfDay()) {
                // Ore di permesso (o vecchia mezza giornata): tutto nel giorno della richiesta.
                put(r.getStartDate(), year, r.getAmount(), all.get(r.getType()), team.get(r.getType()), reasonAcc, teamReasonAcc);
                years.add(r.getStartDate().getYear());
            } else {
                for (LocalDate d = r.getStartDate(); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
                    if (HolidayCalendar.isWeekend(d) || hol.containsKey(d)) continue;
                    put(d, year, BigDecimal.ONE, all.get(r.getType()), team.get(r.getType()), reasonAcc, teamReasonAcc);
                    years.add(d.getYear());
                }
            }
            if (!today.isBefore(r.getStartDate()) && !today.isAfter(r.getEndDate()) && r.getUser().isActive()) {
                User u = r.getUser();
                out.add(new OutToday(u.getId(), u.getFullName(), u.getTeam() == null ? null : u.getTeam().getName(),
                    r.getType().name(), r.getLeaveReason() == null ? null : r.getLeaveReason().label));
            }
        }

        List<TeamStats> teamStats = new ArrayList<>();
        for (Team t : teams.findAllByOrderByName()) {
            int people = headcount.getOrDefault(t.getId(), 0);
            Map<AbsenceType, Acc> acc = byTeam.get(t.getId());
            if (people == 0 && acc == null) continue;
            teamStats.add(new TeamStats(t.getId(), t.getName(), people, views(acc == null ? accs() : acc), reasons(byTeamReason.get(t.getId()))));
        }
        if (headcount.containsKey(NO_TEAM) || byTeam.containsKey(NO_TEAM)) {
            Map<AbsenceType, Acc> acc = byTeam.get(NO_TEAM);
            teamStats.add(new TeamStats(null, "Senza team", headcount.getOrDefault(NO_TEAM, 0), views(acc == null ? accs() : acc), reasons(byTeamReason.get(NO_TEAM))));
        }

        List<ReasonStats> reasons = reasons(byReason);
        out.sort(Comparator.comparing(OutToday::name));
        return new StatsView(year, today, new ArrayList<>(years), active.size(), views(all), reasons, teamStats, out);
    }

    private static final long NO_TEAM = -1L;

    private static long teamKey(User u) {
        return u.teamId() == null ? NO_TEAM : u.teamId();
    }

    private static void put(LocalDate d, int year, BigDecimal v, Acc a, Acc b, Acc c, Acc e) {
        a.add(d, year, v);
        b.add(d, year, v);
        if (c != null) c.add(d, year, v);
        if (e != null) e.add(d, year, v);
    }

    private static List<ReasonStats> reasons(Map<LeaveReason, Acc> m) {
        List<ReasonStats> out = new ArrayList<>();
        if (m != null) m.forEach((k, a) -> out.add(new ReasonStats(k.name(), k.label, a.view())));
        return out;
    }
}
