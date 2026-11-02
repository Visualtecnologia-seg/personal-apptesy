-- Verificação em duas etapas do painel
CREATE TABLE IF NOT EXISTS panel_2fa_ (
  user_id BIGINT PRIMARY KEY,
  secret TEXT,
  pending_secret TEXT,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  last_step BIGINT NOT NULL DEFAULT 0,
  enabled_at TIMESTAMP,
  updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS panel_2fa_recovery_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  code_hash VARCHAR(80) NOT NULL,
  used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_2fa_rec_user ON panel_2fa_recovery_ (user_id);
