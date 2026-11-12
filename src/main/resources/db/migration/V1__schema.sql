CREATE TABLE areas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    manager_id BIGINT NULL
);

CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    area_id BIGINT NOT NULL,
    leader_id BIGINT NULL,
    CONSTRAINT fk_team_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT uq_team_area_name UNIQUE (area_id, name)
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(60) NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL UNIQUE,
    full_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    area_id BIGINT NULL,
    team_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    annual_leave_days DECIMAL(5,1) NOT NULL DEFAULT 26,
    annual_permit_hours DECIMAL(6,1) NOT NULL DEFAULT 32,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

ALTER TABLE areas ADD CONSTRAINT fk_area_manager FOREIGN KEY (manager_id) REFERENCES users (id);
ALTER TABLE teams ADD CONSTRAINT fk_team_leader FOREIGN KEY (leader_id) REFERENCES users (id);

CREATE TABLE absence_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    half_day BOOLEAN NOT NULL DEFAULT FALSE,
    hours DECIMAL(4,1) NULL,
    amount DECIMAL(6,1) NOT NULL,
    note VARCHAR(500) NULL,
    decision_note VARCHAR(500) NULL,
    decided_by_id BIGINT NULL,
    decided_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_req_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_req_decider FOREIGN KEY (decided_by_id) REFERENCES users (id)
);
CREATE INDEX ix_req_user_dates ON absence_requests (user_id, start_date, end_date);
CREATE INDEX ix_req_dates ON absence_requests (start_date, end_date);

CREATE TABLE company_holidays (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    request_id BIGINT NULL,
    action VARCHAR(40) NOT NULL,
    detail VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_request ON audit_events (request_id);
