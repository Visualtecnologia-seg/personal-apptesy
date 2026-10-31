-- Foto de perfil guardada no próprio banco (sem depender da Amazon S3)
CREATE TABLE IF NOT EXISTS user_avatar_ (
  user_id      BIGINT PRIMARY KEY,
  content_type VARCHAR(40) NOT NULL,
  data         BYTEA NOT NULL,
  updated_at   TIMESTAMP NOT NULL
);
