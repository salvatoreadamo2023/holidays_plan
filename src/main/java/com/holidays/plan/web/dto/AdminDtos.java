package com.holidays.plan.web.dto;

import com.holidays.plan.domain.Role;
import com.holidays.plan.domain.User;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserAdminView(Long id, String username, String email, String fullName, Role role,
                                Long areaId, String areaName, Long teamId, String teamName, boolean active,
                                BigDecimal annualLeaveDays, BigDecimal annualPermitHours, boolean mustChangePassword) {
        public static UserAdminView of(User u) {
            return new UserAdminView(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getRole(),
                u.areaId(), u.getArea() == null ? null : u.getArea().getName(),
                u.teamId(), u.getTeam() == null ? null : u.getTeam().getName(), u.isActive(),
                u.getAnnualLeaveDays(), u.getAnnualPermitHours(), u.isMustChangePassword());
        }
    }

    public record UserCreate(
        @NotBlank @Size(min = 3, max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 10, max = 200) String password,
        @NotNull Role role,
        Long areaId,
        Long teamId,
        @DecimalMin("0") @DecimalMax("60") BigDecimal annualLeaveDays,
        @DecimalMin("0") @DecimalMax("400") BigDecimal annualPermitHours) {}

    public record UserUpdate(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull Role role,
        Long areaId,
        Long teamId,
        boolean active,
        @NotNull @DecimalMin("0") @DecimalMax("60") BigDecimal annualLeaveDays,
        @NotNull @DecimalMin("0") @DecimalMax("400") BigDecimal annualPermitHours,
        @Size(min = 10, max = 200) String newPassword) {}

    public record AreaInput(@NotBlank @Size(max = 100) String name, Long managerId) {}

    public record AreaView(Long id, String name, Long managerId, String managerName) {}

    public record TeamInput(@NotBlank @Size(max = 100) String name, @NotNull Long areaId, Long leaderId) {}

    public record TeamView(Long id, String name, Long areaId, String areaName, Long leaderId, String leaderName) {}

    public record HolidayInput(@NotNull LocalDate date, @NotBlank @Size(max = 100) String name) {}

    public record HolidayView(Long id, LocalDate date, String name, boolean national) {}
}
