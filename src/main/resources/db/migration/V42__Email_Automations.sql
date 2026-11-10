-- E-mails automáticos: quem aceita receber lembretes e o registro do que já foi enviado (para não repetir)
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS email_marketing BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TABLE IF NOT EXISTS email_log_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  kind VARCHAR(30) NOT NULL,
  ref VARCHAR(40) NOT NULL DEFAULT '',
  sent_at TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (user_id, kind, ref)
);
CREATE INDEX IF NOT EXISTS ix_email_log_kind ON email_log_ (kind, sent_at);
