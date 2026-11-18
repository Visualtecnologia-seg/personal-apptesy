-- Pedido de exclusão de conta feito pelo site (sem precisar do app): link por e-mail, vale 1 hora
CREATE TABLE IF NOT EXISTS account_delete_token_ (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_account_delete_token_user ON account_delete_token_ (user_id);
