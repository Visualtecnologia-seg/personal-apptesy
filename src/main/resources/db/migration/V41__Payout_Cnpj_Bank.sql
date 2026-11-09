-- Recebimento: CNPJ (MEI) conferido e conta bancária no CPF ou CNPJ da própria pessoa
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS cnpj VARCHAR(14);
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS cnpj_name VARCHAR(200);
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS cnpj_verified_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_cnpj ON user_ (cnpj) WHERE cnpj IS NOT NULL;

CREATE TABLE IF NOT EXISTS bank_account_ (
  user_id BIGINT PRIMARY KEY REFERENCES user_(id) ON DELETE CASCADE,
  bank_code VARCHAR(3) NOT NULL,
  bank_name VARCHAR(80),
  agency VARCHAR(5) NOT NULL,
  account VARCHAR(13) NOT NULL,
  account_digit VARCHAR(2) NOT NULL,
  account_type VARCHAR(20) NOT NULL,
  owner_doc VARCHAR(14) NOT NULL,
  owner_name VARCHAR(200) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
