-- Saldo dell'anno precedente (AP) accanto a quello dell'anno corrente.
ALTER TABLE leave_balances ADD COLUMN prev_leave_days NUMERIC(6, 2) NULL;
ALTER TABLE leave_balances ADD COLUMN prev_permit_hours NUMERIC(7, 2) NULL;

-- Impostazioni generali modificabili dall'admin (chiave/valore).
CREATE TABLE app_settings (
    setting_key VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(2000) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL
);

-- Notifiche visibili nel sito (campanella), salvate insieme alle push.
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000) NULL,
    screen VARCHAR(40) NULL,
    request_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- Reset password con link via email: nel DB solo l'hash SHA-256 del token, monouso, 30 minuti.
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    ip VARCHAR(45) NULL
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);

-- Dopo un reset con link, i token di accesso emessi prima non valgono piu'.
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMP NULL;
