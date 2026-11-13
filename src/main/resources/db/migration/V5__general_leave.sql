-- La malattia diventa una tipologia del General leave; nuovi tipi CO (presso cliente) e BT (trasferta).
ALTER TABLE absence_requests ADD COLUMN leave_reason VARCHAR(30);
UPDATE absence_requests SET type = 'GENERAL_LEAVE', leave_reason = 'MALATTIA' WHERE type = 'MALATTIA';
