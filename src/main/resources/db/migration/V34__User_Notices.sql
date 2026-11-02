-- Caixa de avisos dentro do app (chega mesmo sem notificação do celular configurada)
CREATE TABLE IF NOT EXISTS user_notice_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  app VARCHAR(10) NOT NULL,
  text VARCHAR(500) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  read_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_notice_user ON user_notice_ (user_id, app, id);
