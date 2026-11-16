-- Scelta aziendale: per ora i permessi si chiedono solo da 2 o 4 ore.
-- Vale da subito; poi l'admin puo' cambiarlo quando vuole da Admin > Impostazioni.
DELETE FROM app_settings WHERE setting_key = 'permit.hours';
INSERT INTO app_settings (setting_key, setting_value, updated_at) VALUES ('permit.hours', '2,4', CURRENT_TIMESTAMP);
