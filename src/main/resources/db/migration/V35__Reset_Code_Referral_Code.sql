-- Recuperação de senha por código (o código vale 15 minutos e 5 tentativas; a senha só muda quando a pessoa confirma)
CREATE TABLE IF NOT EXISTS password_reset_ (
  user_id     BIGINT PRIMARY KEY REFERENCES user_(id) ON DELETE CASCADE,
  code_hash   VARCHAR(100) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  attempts    INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  sends_day   DATE,
  sends       INT NOT NULL DEFAULT 0
);

-- Código de indicação próprio (antes era o e-mail/usuário da pessoa)
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS referral_code VARCHAR(12);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_referral_code ON user_ (referral_code);
