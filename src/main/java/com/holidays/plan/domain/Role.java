package com.holidays.plan.domain;

/** Gerarchia: ADMIN > PROJECT_MANAGER > TEAM_LEAD > USER. */
public enum Role {
    USER(0), TEAM_LEAD(1), PROJECT_MANAGER(2), ADMIN(3);

    private final int level;

    Role(int level) { this.level = level; }

    public int level() { return level; }

    public boolean isManager() { return this != USER; }
}
