-- Confirmação do telefone por SMS no cadastro
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS phone_code_ (
  phone       VARCHAR(20) PRIMARY KEY,
  code_hash   VARCHAR(100) NOT NULL,
  expires_at  TIMESTAMP NOT NULL,
  attempts    INT NOT NULL DEFAULT 0,
  sends       INT NOT NULL DEFAULT 0,
  sends_day   DATE,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
