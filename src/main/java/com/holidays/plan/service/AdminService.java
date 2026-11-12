package com.holidays.plan.service;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.*;
import com.holidays.plan.web.dto.AdminDtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class AdminService {
    private final UserRepository users;
    private final AreaRepository areas;
    private final TeamRepository teams;
    private final CompanyHolidayRepository companyHolidays;
    private final HolidayCalendar calendar;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(UserRepository users, AreaRepository areas, TeamRepository teams,
                        CompanyHolidayRepository companyHolidays, HolidayCalendar calendar,
                        PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.areas = areas;
        this.teams = teams;
        this.companyHolidays = companyHolidays;
        this.calendar = calendar;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ---------- utenti ----------
    @Transactional(readOnly = true)
    public List<UserAdminView> listUsers() {
        return users.findAllByOrderByFullName().stream().map(UserAdminView::of).toList();
    }

    private void applyOrg(User u, Role role, Long areaId, Long teamId) {
        Area area = areaId == null ? null : areas.findById(areaId).orElseThrow(() -> ApiException.bad("Area inesistente"));
        Team team = teamId == null ? null : teams.findById(teamId).orElseThrow(() -> ApiException.bad("Team inesistente"));
        if (team != null && (area == null || !Objects.equals(team.getArea().getId(), area.getId())))
            throw ApiException.bad("Il team non appartiene all'area scelta");
        if (role != Role.ADMIN && area == null) throw ApiException.bad("Assegna un'area all'utente");
        u.setRole(role);
        u.setArea(area);
        u.setTeam(team);
    }

    public UserAdminView createUser(User actor, UserCreate in) {
        if (users.existsByUsernameIgnoreCase(in.username())) throw ApiException.conflict("Username gia' in uso");
        if (users.existsByEmailIgnoreCase(in.email())) throw ApiException.conflict("Email gia' in uso");
        User u = new User();
        u.setUsername(in.username().trim());
        u.setEmail(in.email().trim());
        u.setFullName(in.fullName().trim());
        u.setPasswordHash(encoder.encode(in.password()));
        u.setMustChangePassword(true);
        u.setAnnualLeaveDays(in.annualLeaveDays() == null ? BigDecimal.ZERO : in.annualLeaveDays());
        u.setAnnualPermitHours(in.annualPermitHours() == null ? BigDecimal.ZERO : in.annualPermitHours());
        applyOrg(u, in.role(), in.areaId(), in.teamId());
        u = users.save(u);
        audit.log(actor.getId(), null, "USER_CREATED", u.getUsername() + " " + u.getRole());
        return UserAdminView.of(u);
    }

    public UserAdminView updateUser(User actor, Long id, UserUpdate in) {
        User u = users.findById(id).orElseThrow(ApiException::notFound);
        if (Objects.equals(actor.getId(), id) && (in.role() != Role.ADMIN || !in.active()))
            throw ApiException.bad("Non puoi togliere a te stesso il ruolo di Admin o disattivarti");
        if (!u.getEmail().equalsIgnoreCase(in.email()) && users.existsByEmailIgnoreCase(in.email()))
            throw ApiException.conflict("Email gia' in uso");
        u.setEmail(in.email().trim());
        u.setFullName(in.fullName().trim());
        u.setActive(in.active());
        if (in.annualLeaveDays() != null) u.setAnnualLeaveDays(in.annualLeaveDays());
        if (in.annualPermitHours() != null) u.setAnnualPermitHours(in.annualPermitHours());
        applyOrg(u, in.role(), in.areaId(), in.teamId());
        if (in.newPassword() != null && !in.newPassword().isBlank()) {
            u.setPasswordHash(encoder.encode(in.newPassword()));
            u.setMustChangePassword(true);
            audit.log(actor.getId(), null, "PASSWORD_RESET", u.getUsername());
        }
        audit.log(actor.getId(), null, "USER_UPDATED", u.getUsername() + " " + u.getRole() + " active=" + u.isActive());
        return UserAdminView.of(u);
    }

    // ---------- aree ----------
    private AreaView view(Area a) {
        return new AreaView(a.getId(), a.getName(), a.getManager() == null ? null : a.getManager().getId(),
            a.getManager() == null ? null : a.getManager().getFullName());
    }

    @Transactional(readOnly = true)
    public List<AreaView> listAreas() {
        return areas.findAllByOrderByName().stream().map(this::view).toList();
    }

    private User managerFor(Long userId, Role expected) {
        if (userId == null) return null;
        User m = users.findById(userId).orElseThrow(() -> ApiException.bad("Utente inesistente"));
        if (m.getRole() != expected) throw ApiException.bad("L'utente scelto deve avere ruolo " + expected);
        return m;
    }

    public AreaView saveArea(Long id, AreaInput in) {
        Area a = id == null ? new Area() : areas.findById(id).orElseThrow(ApiException::notFound);
        a.setName(in.name().trim());
        a.setManager(managerFor(in.managerId(), Role.PROJECT_MANAGER));
        return view(areas.save(a));
    }

    // ---------- team ----------
    private TeamView view(Team t) {
        return new TeamView(t.getId(), t.getName(), t.getArea().getId(), t.getArea().getName(),
            t.getLeader() == null ? null : t.getLeader().getId(),
            t.getLeader() == null ? null : t.getLeader().getFullName());
    }

    @Transactional(readOnly = true)
    public List<TeamView> listTeams() {
        return teams.findAllByOrderByName().stream().map(this::view).toList();
    }

    public TeamView saveTeam(Long id, TeamInput in) {
        Team t = id == null ? new Team() : teams.findById(id).orElseThrow(ApiException::notFound);
        t.setName(in.name().trim());
        t.setArea(areas.findById(in.areaId()).orElseThrow(() -> ApiException.bad("Area inesistente")));
        t.setLeader(managerFor(in.leaderId(), Role.TEAM_LEAD));
        return view(teams.save(t));
    }

    // ---------- festivita' ----------
    @Transactional(readOnly = true)
    public List<HolidayView> listHolidays(int year) {
        LocalDate from = LocalDate.of(year, 1, 1), to = LocalDate.of(year, 12, 31);
        Map<LocalDate, Long> custom = new java.util.HashMap<>();
        companyHolidays.findByDateBetweenOrderByDate(from, to).forEach(h -> custom.put(h.getDate(), h.getId()));
        List<HolidayView> out = new ArrayList<>();
        calendar.holidays(from, to).forEach((d, name) ->
            out.add(new HolidayView(custom.get(d), d, name, !custom.containsKey(d))));
        return out;
    }

    public HolidayView addHoliday(HolidayInput in) {
        if (companyHolidays.existsByDate(in.date())) throw ApiException.conflict("Esiste gia' una chiusura in quella data");
        CompanyHoliday h = new CompanyHoliday();
        h.setDate(in.date());
        h.setName(in.name().trim());
        h = companyHolidays.save(h);
        return new HolidayView(h.getId(), h.getDate(), h.getName(), false);
    }

    public void deleteHoliday(Long id) {
        companyHolidays.delete(companyHolidays.findById(id).orElseThrow(ApiException::notFound));
    }
}
