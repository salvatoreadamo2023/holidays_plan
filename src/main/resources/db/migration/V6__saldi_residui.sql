-- Saldo residuo ufficiale (da busta paga) caricato dall'admin con un Excel: solo informativo, non blocca le richieste.
CREATE TABLE leave_balances (
    user_id BIGINT PRIMARY KEY,
    reference_month DATE NOT NULL,
    leave_days NUMERIC(6, 2) NULL,
    permit_hours NUMERIC(7, 2) NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by BIGINT NULL,
    CONSTRAINT fk_balance_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_balance_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id)
);
