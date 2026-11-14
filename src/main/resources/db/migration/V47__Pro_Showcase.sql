-- Capa do perfil do profissional (pode ser de uma parceria com uma marca) e indicações de produtos
CREATE TABLE IF NOT EXISTS pro_cover_ (
  user_id BIGINT PRIMARY KEY,
  content_type VARCHAR(30) NOT NULL,
  data BYTEA NOT NULL,
  mode VARCHAR(10) NOT NULL DEFAULT 'PLAIN',
  brand VARCHAR(40),
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(200),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pro_reco_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(80) NOT NULL,
  note VARCHAR(160),
  url VARCHAR(600) NOT NULL,
  store VARCHAR(30) NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'RECO',
  featured BOOLEAN NOT NULL DEFAULT FALSE,
  position INT NOT NULL DEFAULT 0,
  content_type VARCHAR(30),
  data BYTEA,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(200),
  clicks INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS pro_reco_user_idx ON pro_reco_ (user_id);
CREATE INDEX IF NOT EXISTS pro_reco_status_idx ON pro_reco_ (status);
CREATE TABLE IF NOT EXISTS pro_showcase_stat_ (
  user_id BIGINT NOT NULL,
  month DATE NOT NULL,
  views INT NOT NULL DEFAULT 0,
  clicks INT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, month)
);
