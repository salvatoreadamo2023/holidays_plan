package com.holidays.plan.web;

import com.holidays.plan.security.CurrentUser;
import com.holidays.plan.service.AdminService;
import com.holidays.plan.web.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService admin;
    private final CurrentUser currentUser;

    public AdminController(AdminService admin, CurrentUser currentUser) {
        this.admin = admin;
        this.currentUser = currentUser;
    }

    @GetMapping("/users")
    public List<UserAdminView> users() {
        currentUser.get();
        return admin.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminView createUser(@Valid @RequestBody UserCreate in) {
        return admin.createUser(currentUser.get(), in);
    }

    @PutMapping("/users/{id}")
    public UserAdminView updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdate in) {
        return admin.updateUser(currentUser.get(), id, in);
    }

    @GetMapping("/areas")
    public List<AreaView> areas() {
        currentUser.get();
        return admin.listAreas();
    }

    @PostMapping("/areas")
    @ResponseStatus(HttpStatus.CREATED)
    public AreaView createArea(@Valid @RequestBody AreaInput in) {
        currentUser.get();
        return admin.saveArea(null, in);
    }

    @PutMapping("/areas/{id}")
    public AreaView updateArea(@PathVariable Long id, @Valid @RequestBody AreaInput in) {
        currentUser.get();
        return admin.saveArea(id, in);
    }

    @GetMapping("/teams")
    public List<TeamView> teams() {
        currentUser.get();
        return admin.listTeams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamView createTeam(@Valid @RequestBody TeamInput in) {
        currentUser.get();
        return admin.saveTeam(null, in);
    }

    @PutMapping("/teams/{id}")
    public TeamView updateTeam(@PathVariable Long id, @Valid @RequestBody TeamInput in) {
        currentUser.get();
        return admin.saveTeam(id, in);
    }

    @GetMapping("/holidays")
    public List<HolidayView> holidays(@RequestParam(required = false) Integer year) {
        currentUser.get();
        return admin.listHolidays(year == null ? LocalDate.now(com.holidays.plan.config.ClockConfig.ZONE).getYear() : year);
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayView addHoliday(@Valid @RequestBody HolidayInput in) {
        currentUser.get();
        return admin.addHoliday(in);
    }

    @DeleteMapping("/holidays/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) {
        currentUser.get();
        admin.deleteHoliday(id);
    }
}
