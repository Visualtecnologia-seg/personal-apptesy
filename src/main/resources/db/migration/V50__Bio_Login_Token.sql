-- Entrar com a digital: o celular guarda uma chave própria (não a senha). Só o resumo (SHA-256) fica aqui.
CREATE TABLE IF NOT EXISTS bio_token_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES user_(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  device_id VARCHAR(80),
  token_version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_used TIMESTAMP
);
CREATE INDEX IF NOT EXISTS bio_token_user_idx ON bio_token_(user_id);
