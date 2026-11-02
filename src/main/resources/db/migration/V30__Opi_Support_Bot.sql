-- Opi: assistente do suporte (primeiro atendimento)
ALTER TABLE support_message_ ADD COLUMN IF NOT EXISTS bot BOOLEAN DEFAULT FALSE;
ALTER TABLE support_message_ ADD COLUMN IF NOT EXISTS buttons TEXT;
CREATE TABLE IF NOT EXISTS support_state_ (
  user_id BIGINT PRIMARY KEY,
  human BOOLEAN NOT NULL DEFAULT FALSE,
  priority BOOLEAN NOT NULL DEFAULT FALSE,
  misses INT NOT NULL DEFAULT 0,
  topic VARCHAR(60),
  updated_at TIMESTAMP
);
