-- V21: rede social do profissional, chamadas, suporte, deslocamento/cancelamento por km e orçamento extra

-- Posts do profissional (só fotos)
CREATE TABLE IF NOT EXISTS post_ (
  id              UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  caption         TEXT,
  created_at      TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_post_pro ON post_ (professional_id, created_at DESC);

CREATE TABLE IF NOT EXISTS post_photo_ (
  id           UUID PRIMARY KEY,
  post_id      UUID NOT NULL,
  position     INT NOT NULL,
  content_type VARCHAR(40) NOT NULL,
  data         BYTEA NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_post_photo_post ON post_photo_ (post_id);

CREATE TABLE IF NOT EXISTS post_like_ (
  post_id    UUID NOT NULL,
  user_id    BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (post_id, user_id)
);

-- Só quem foi atendido comenta; a nota (1 a 5) é única por pedido
CREATE TABLE IF NOT EXISTS post_comment_ (
  id         UUID PRIMARY KEY,
  post_id    UUID NOT NULL,
  user_id    BIGINT NOT NULL,
  user_name  VARCHAR(120),
  order_id   UUID,
  text       TEXT NOT NULL,
  rating     INT,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_post_comment_post ON post_comment_ (post_id, created_at);

-- Chamadas de áudio/vídeo (registro para provas)
CREATE TABLE IF NOT EXISTS call_ (
  id          UUID PRIMARY KEY,
  order_id    UUID NOT NULL,
  caller_id   BIGINT NOT NULL,
  callee_id   BIGINT NOT NULL,
  kind        VARCHAR(10) NOT NULL,
  status      VARCHAR(12) NOT NULL,
  created_at  TIMESTAMP NOT NULL,
  answered_at TIMESTAMP,
  ended_at    TIMESTAMP,
  ended_by    BIGINT
);
CREATE INDEX IF NOT EXISTS idx_call_order ON call_ (order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_callee ON call_ (callee_id, status);

CREATE TABLE IF NOT EXISTS call_signal_ (
  id         BIGSERIAL PRIMARY KEY,
  call_id    UUID NOT NULL,
  from_user  BIGINT NOT NULL,
  kind       VARCHAR(12) NOT NULL,
  payload    TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_call_signal_call ON call_signal_ (call_id, id);

-- Chat de suporte (respondido pelo painel)
CREATE TABLE IF NOT EXISTS support_message_ (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  from_admin    BOOLEAN NOT NULL,
  admin_name    VARCHAR(120),
  text          TEXT NOT NULL,
  created_at    TIMESTAMP NOT NULL,
  read_by_user  BOOLEAN NOT NULL DEFAULT FALSE,
  read_by_admin BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_support_user ON support_message_ (user_id, id);

-- "Saí para o atendimento", localização ao vivo e taxa de cancelamento
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS departed_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS depart_lat DOUBLE PRECISION;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS depart_lng DOUBLE PRECISION;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pro_lat DOUBLE PRECISION;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pro_lng DOUBLE PRECISION;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pro_loc_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS cancel_fee NUMERIC(15, 2);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS client_refund NUMERIC(15, 2);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS dispute_resolution TEXT;

-- Orçamento extra durante o serviço (cobrado à parte)
CREATE TABLE IF NOT EXISTS order_extra_ (
  id             UUID PRIMARY KEY,
  order_id       UUID NOT NULL,
  message_id     UUID,
  description    TEXT,
  service_value  NUMERIC(15, 2) NOT NULL,
  client_total   NUMERIC(15, 2) NOT NULL,
  status         VARCHAR(16) NOT NULL,
  pix_payment_id VARCHAR(80),
  pix_copy_paste TEXT,
  pix_qr         TEXT,
  released       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMP NOT NULL,
  paid_at        TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_order_extra_order ON order_extra_ (order_id);
CREATE INDEX IF NOT EXISTS idx_order_extra_pix ON order_extra_ (pix_payment_id);
