-- Tabela de favoritos antiga (V1) não tinha a data
ALTER TABLE favorite_ ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();
UPDATE favorite_ SET created_at = NOW() WHERE created_at IS NULL;
