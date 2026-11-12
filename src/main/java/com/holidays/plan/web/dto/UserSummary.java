package com.holidays.plan.web.dto;

import com.holidays.plan.domain.Role;
import com.holidays.plan.domain.User;

public record UserSummary(Long id, String fullName, String username, Role role,
                          Long areaId, String areaName, Long teamId, String teamName,
                          boolean mustChangePassword) {
    public static UserSummary of(User u) {
        return new UserSummary(u.getId(), u.getFullName(), u.getUsername(), u.getRole(),
            u.areaId(), u.getArea() == null ? null : u.getArea().getName(),
            u.teamId(), u.getTeam() == null ? null : u.getTeam().getName(), u.isMustChangePassword());
    }
}
