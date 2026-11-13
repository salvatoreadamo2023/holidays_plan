-- Rettifica: una nuova richiesta che, se approvata, sostituisce una richiesta gia' approvata.
ALTER TABLE absence_requests ADD COLUMN amends_id BIGINT REFERENCES absence_requests(id);
CREATE INDEX idx_absence_requests_amends ON absence_requests(amends_id);
