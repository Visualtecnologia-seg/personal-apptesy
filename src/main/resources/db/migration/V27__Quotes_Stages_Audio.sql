-- Orçamento para vários profissionais, pagamento por etapas e áudio no chat
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS quote_group UUID;
CREATE INDEX IF NOT EXISTS idx_order_quote_group ON order_ (quote_group);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS request_note TEXT;
CREATE TABLE IF NOT EXISTS order_stage_ (
  order_id UUID NOT NULL,
  idx INT NOT NULL,
  percent INT NOT NULL,
  label VARCHAR(80),
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',   -- PENDING | DONE | APPROVED
  done_at TIMESTAMP,
  approved_at TIMESTAMP,
  PRIMARY KEY (order_id, idx)
);
CREATE TABLE IF NOT EXISTS chat_audio_ (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL,
  sender_id BIGINT NOT NULL,
  content_type VARCHAR(40) NOT NULL,
  data BYTEA NOT NULL,
  seconds INT,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chat_audio_order ON chat_audio_ (order_id);
