-- Cambio password obbligatorio al primo accesso e saldi iniziali a zero (vanno caricati dall'admin).
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN annual_leave_days SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN annual_permit_hours SET DEFAULT 0;
UPDATE users SET annual_leave_days = 0, annual_permit_hours = 0;
-- Tutti gli account esistenti hanno password iniziali condivise: vanno cambiate al prossimo accesso.
UPDATE users SET must_change_password = TRUE;
