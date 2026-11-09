-- Códigos de confirmação (SMS e e-mail) num lugar só
CREATE TABLE IF NOT EXISTS verify_code_ (
  channel     VARCHAR(10) NOT NULL,
  target      VARCHAR(160) NOT NULL,
  code_hash   VARCHAR(100) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  attempts    INT NOT NULL DEFAULT 0,
  sends       INT NOT NULL DEFAULT 0,
  sends_day   DATE,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (channel, target)
);
-- Aceite dos termos (LGPD), e-mail confirmado e login com Google
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS terms_version VARCHAR(20);
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_google_sub ON user_ (google_sub) WHERE google_sub IS NOT NULL;
