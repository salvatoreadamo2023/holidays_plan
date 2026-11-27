-- Matricola della persona (colonna DIPENDENTE del report ratei del cedolino).
ALTER TABLE users ADD COLUMN employee_code VARCHAR(20) NULL;
CREATE UNIQUE INDEX ux_users_employee_code ON users (employee_code);

-- Ratei reali dall'ultimo cedolino caricato, una riga per persona e voce (Ferie, Perm.Ex-Fs, Rol/Par, ExFs-Rol...).
-- Le voci restano separate; nel riepilogo i permessi (unita' H) si sommano in un'unica colonna.
CREATE TABLE payroll_items (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code VARCHAR(20) NOT NULL,
    description VARCHAR(100) NULL,
    unit VARCHAR(1) NOT NULL,
    reference_month DATE NOT NULL,
    annual_qty NUMERIC(9, 5) NULL,
    prev_residual NUMERIC(9, 5) NULL,
    prev_used NUMERIC(9, 5) NULL,
    prev_balance NUMERIC(9, 5) NULL,
    cur_accrued NUMERIC(9, 5) NULL,
    cur_used NUMERIC(9, 5) NULL,
    cur_balance NUMERIC(9, 5) NULL,
    total_residual NUMERIC(9, 5) NULL,
    still_accruable NUMERIC(9, 5) NULL,
    total_projected NUMERIC(9, 5) NULL,
    non_compensable NUMERIC(9, 5) NULL,
    deadline DATE NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, code)
);
