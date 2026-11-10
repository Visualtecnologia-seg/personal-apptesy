-- Quando o profissional ficou online pela última vez (para o lembrete de quem está parado)
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS online_at TIMESTAMP;
UPDATE professional_ SET online_at = NOW() WHERE COALESCE(is_online, FALSE) = TRUE;
