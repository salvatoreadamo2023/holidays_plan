package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.AbsenceRequestRepository;
import com.holidays.plan.repo.AreaRepository;
import com.holidays.plan.repo.TeamRepository;
import com.holidays.plan.repo.UserRepository;
import com.holidays.plan.web.dto.CalendarView;
import com.holidays.plan.web.dto.CalendarView.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CalendarService {
    private final UserRepository users;
    private final AreaRepository areas;
    private final TeamRepository teams;
    private final AbsenceRequestRepository requests;
    private final HolidayCalendar holidays;
    private final AccessPolicy policy;

    public CalendarService(UserRepository users, AreaRepository areas, TeamRepository teams,
                           AbsenceRequestRepository requests, HolidayCalendar holidays, AccessPolicy policy) {
        this.users = users;
        this.areas = areas;
        this.teams = teams;
        this.requests = requests;
        this.holidays = holidays;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public CalendarView view(User actor, LocalDate from, LocalDate to, Long areaId, Long teamId) {
        if (to.isBefore(from) || from.plusDays(92).isBefore(to)) throw ApiException.bad("Intervallo non valido (max 3 mesi)");
        boolean admin = actor.getRole() == Role.ADMIN;
        Long area = areaId != null ? areaId : (admin ? null : actor.areaId());
        if (!admin && (area == null || !policy.canSeeArea(actor, area))) throw ApiException.forbidden();

        List<User> people = (area == null ? users.findByActiveTrueOrderByFullName()
            : users.findByAreaIdAndActiveTrueOrderByFullName(area));
        if (teamId != null) people = people.stream().filter(u -> teamId.equals(u.teamId())).toList();
        Set<Long> ids = people.stream().map(User::getId).collect(Collectors.toSet());

        Map<LocalDate, String> hol = holidays.holidays(from, to);
        List<Day> days = from.datesUntil(to.plusDays(1))
            .map(d -> new Day(d, HolidayCalendar.isWeekend(d), hol.get(d))).toList();

        EnumSet<RequestStatus> statuses = EnumSet.of(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.CANCEL_REQUESTED);
        List<Entry> entries = new ArrayList<>();
        for (AbsenceRequest r : requests.findOverlapping(from, to, statuses)) {
            User u = r.getUser();
            if (!ids.contains(u.getId())) continue;
            boolean details = policy.canSeeDetails(actor, u);
            // Chi non ha visibilita' completa vede solo assenze confermate, senza tipo.
            if (!details && r.getStatus() == RequestStatus.PENDING) continue;
            entries.add(new Entry(details ? r.getId() : null, u.getId(), r.getStartDate(), r.getEndDate(),
                details ? r.getType().name() : "ASSENZA", r.getStatus().name(), r.isHalfDay(),
                details ? r.getHours() : null));
        }

        List<Option> areaOptions = admin
            ? areas.findAllByOrderByName().stream().map(a -> new Option(a.getId(), a.getName())).toList()
            : (actor.getArea() == null ? List.of() : List.of(new Option(actor.getArea().getId(), actor.getArea().getName())));
        List<Option> teamOptions = (area == null ? teams.findAllByOrderByName() : teams.findByAreaIdOrderByName(area))
            .stream().map(t -> new Option(t.getId(), t.getName())).toList();

        List<Person> persons = people.stream().map(u -> new Person(u.getId(), u.getFullName(), u.getRole().name(),
            u.teamId(), u.getTeam() == null ? null : u.getTeam().getName())).toList();
        return new CalendarView(from, to, days, persons, entries, areaOptions, teamOptions, area, teamId);
    }

    /** Persone assenti oggi nell'area visibile all'utente. */
    @Transactional(readOnly = true)
    public List<String> absentToday(User actor) {
        LocalDate today = LocalDate.now(com.holidays.plan.config.ClockConfig.ZONE);
        boolean admin = actor.getRole() == Role.ADMIN;
        return requests.findOverlapping(today, today, RequestStatus.ACTIVE).stream()
            .map(AbsenceRequest::getUser)
            .filter(u -> admin || (actor.areaId() != null && actor.areaId().equals(u.areaId())))
            .map(User::getFullName).distinct().sorted().toList();
    }
}
