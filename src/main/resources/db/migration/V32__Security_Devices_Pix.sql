-- Segurança: versão dos tokens (derrubar acessos), aparelhos conhecidos, chave PIX
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS token_version BIGINT DEFAULT 0;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS pix_key_changed_at TIMESTAMP;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Aparelhos onde cada pessoa já entrou (para avisar de celular novo)
CREATE TABLE IF NOT EXISTS user_device_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  device_id VARCHAR(80) NOT NULL,
  device_name VARCHAR(120),
  first_seen TIMESTAMP NOT NULL,
  last_seen TIMESTAMP NOT NULL,
  ip VARCHAR(64),
  UNIQUE (user_id, device_id)
);
CREATE INDEX IF NOT EXISTS idx_user_device_dev ON user_device_ (device_id);

-- Alertas de segurança ("não fui eu", saque segurado, etc.)
CREATE TABLE IF NOT EXISTS security_event_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT,
  kind VARCHAR(40) NOT NULL,
  detail VARCHAR(400),
  created_at TIMESTAMP NOT NULL,
  seen BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_security_event_at ON security_event_ (created_at);

-- Toda troca de chave PIX fica registrada (por qualquer caminho do sistema)
CREATE TABLE IF NOT EXISTS pix_key_change_ (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  old_key_end VARCHAR(8),
  new_key_end VARCHAR(8),
  changed_at TIMESTAMP NOT NULL,
  notified BOOLEAN DEFAULT FALSE
);

CREATE OR REPLACE FUNCTION opus_pix_key_changed() RETURNS trigger AS $$
BEGIN
  IF COALESCE(NEW.pix_key, '') <> COALESCE(OLD.pix_key, '') THEN
    INSERT INTO pix_key_change_ (user_id, old_key_end, new_key_end, changed_at)
      VALUES (NEW.id, RIGHT(COALESCE(OLD.pix_key, ''), 4), RIGHT(COALESCE(NEW.pix_key, ''), 4), NOW());
    -- só segura o saque quando já existia uma chave antes (troca, não o primeiro cadastro)
    IF COALESCE(OLD.pix_key, '') <> '' THEN
      NEW.pix_key_changed_at := NOW();
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pix_key_changed ON user_;
CREATE TRIGGER trg_pix_key_changed BEFORE UPDATE OF pix_key ON user_ FOR EACH ROW EXECUTE PROCEDURE opus_pix_key_changed();

-- Quem abriu documentos e selfies no painel; quem pode abrir
CREATE TABLE IF NOT EXISTS doc_access_log_ (
  id BIGSERIAL PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  subject_user_id BIGINT,
  what VARCHAR(60) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS panel_permission_ (
  user_id BIGINT PRIMARY KEY,
  can_view_docs BOOLEAN NOT NULL DEFAULT FALSE
);
-- quem já é do painel hoje continua podendo ver (o dono decide depois)
INSERT INTO panel_permission_ (user_id, can_view_docs) SELECT id, TRUE FROM user_ WHERE username LIKE 'painel:%' ON CONFLICT DO NOTHING;

-- Data do cancelamento (para o alerta de muitos cancelamentos)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
CREATE OR REPLACE FUNCTION opus_order_cancelled() RETURNS trigger AS $$
BEGIN
  IF NEW.status = 'CANCELLED' AND COALESCE(OLD.status, '') <> 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
    NEW.cancelled_at := NOW();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_order_cancelled ON order_;
CREATE TRIGGER trg_order_cancelled BEFORE UPDATE OF status ON order_ FOR EACH ROW EXECUTE PROCEDURE opus_order_cancelled();

-- Link de uso único para baixar os dados (LGPD)
CREATE TABLE IF NOT EXISTS data_export_token_ (
  token VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP
);
