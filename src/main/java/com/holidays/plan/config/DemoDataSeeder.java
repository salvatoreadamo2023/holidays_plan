package com.holidays.plan.config;

import com.holidays.plan.domain.*;
import com.holidays.plan.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Solo profilo dev: crea un'organizzazione di esempio. Password demo: "password123!". */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "password123!";

    private final UserRepository users;
    private final AreaRepository areas;
    private final TeamRepository teams;
    private final AbsenceRequestRepository requests;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository users, AreaRepository areas, TeamRepository teams,
                          AbsenceRequestRepository requests, PasswordEncoder encoder) {
        this.users = users;
        this.areas = areas;
        this.teams = teams;
        this.requests = requests;
        this.encoder = encoder;
    }

    private String hash;

    private User user(String username, String name, Role role, Area area, Team team) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setFullName(name);
        u.setPasswordHash(hash);
        u.setRole(role);
        u.setArea(area);
        u.setTeam(team);
        // Solo dati demo: in produzione i saldi partono da zero e li carica l'admin.
        u.setAnnualLeaveDays(new BigDecimal("26"));
        u.setAnnualPermitHours(new BigDecimal("32"));
        return users.save(u);
    }

    private void req(User u, AbsenceType t, LocalDate from, LocalDate to, BigDecimal amount, RequestStatus st, User decider) {
        AbsenceRequest r = new AbsenceRequest();
        r.setUser(u);
        r.setType(t);
        r.setStartDate(from);
        r.setEndDate(to);
        r.setAmount(amount);
        r.setStatus(st);
        if (t == AbsenceType.PERMESSO) r.setHours(amount);
        if (t == AbsenceType.GENERAL_LEAVE) r.setLeaveReason(com.holidays.plan.domain.LeaveReason.MALATTIA);
        if (st != RequestStatus.PENDING && decider != null) {
            r.setDecidedBy(decider);
            r.setDecidedAt(Instant.now());
        }
        requests.save(r);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        hash = encoder.encode(DEMO_PASSWORD);

        Area dev = new Area();
        dev.setName("Sviluppo");
        dev = areas.save(dev);
        Area ops = new Area();
        ops.setName("Operations");
        ops = areas.save(ops);

        Team web = new Team(); web.setName("Web"); web.setArea(dev); web = teams.save(web);
        Team mobile = new Team(); mobile.setName("Mobile"); mobile.setArea(dev); mobile = teams.save(mobile);
        Team infra = new Team(); infra.setName("Infrastruttura"); infra.setArea(ops); infra = teams.save(infra);

        User admin = user("admin", "Alessia Admin", Role.ADMIN, null, null);
        User pm = user("pm", "Paolo Moretti", Role.PROJECT_MANAGER, dev, null);
        User tl = user("techlead", "Giulia Ricci", Role.TEAM_LEAD, dev, web);
        User tl2 = user("techlead2", "Marco Esposito", Role.TEAM_LEAD, dev, mobile);
        User u1 = user("user", "Luca Bianchi", Role.USER, dev, web);
        User u2 = user("sara", "Sara Colombo", Role.USER, dev, web);
        User u3 = user("davide", "Davide Romano", Role.USER, dev, mobile);
        User u4 = user("elena", "Elena Greco", Role.USER, dev, mobile);
        User pm2 = user("pm2", "Francesca Conti", Role.PROJECT_MANAGER, ops, null);
        User u5 = user("andrea", "Andrea Gallo", Role.USER, ops, infra);

        dev.setManager(pm);
        ops.setManager(pm2);
        web.setLeader(tl);
        mobile.setLeader(tl2);

        LocalDate mon = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        req(u2, AbsenceType.FERIE, mon, mon.plusDays(4), new BigDecimal("5"), RequestStatus.APPROVED, tl);
        req(u3, AbsenceType.FERIE, mon.plusDays(2), mon.plusDays(3), new BigDecimal("2"), RequestStatus.APPROVED, tl2);
        req(u4, AbsenceType.GENERAL_LEAVE, LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(1),
            new BigDecimal("2"), RequestStatus.APPROVED, pm);
        req(u1, AbsenceType.FERIE, mon.plusDays(14), mon.plusDays(18), new BigDecimal("5"), RequestStatus.PENDING, null);
        req(tl, AbsenceType.PERMESSO, mon.plusDays(1), mon.plusDays(1), new BigDecimal("3"), RequestStatus.PENDING, null);
        req(u5, AbsenceType.FERIE, mon.plusDays(7), mon.plusDays(9), new BigDecimal("3"), RequestStatus.APPROVED, pm2);
        req(pm, AbsenceType.FERIE, mon.plusDays(21), mon.plusDays(25), new BigDecimal("5"), RequestStatus.PENDING, null);
        log.warn("Dati demo creati (utenti: admin, pm, techlead, user... password: {})", DEMO_PASSWORD);
    }
}
