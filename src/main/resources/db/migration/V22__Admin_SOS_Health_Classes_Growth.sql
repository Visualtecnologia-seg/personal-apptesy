-- V22: painel administrativo, SOS, saúde, aulas em grupo, pacotes, indicação, lembretes e cupons

-- Configurações editáveis pelo painel (taxa, km, pontos...)
CREATE TABLE IF NOT EXISTS platform_setting_ (
  key        VARCHAR(60) PRIMARY KEY,
  value      VARCHAR(255) NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  updated_by VARCHAR(120)
);

-- Histórico de ações da equipe no painel
CREATE TABLE IF NOT EXISTS admin_action_ (
  id         BIGSERIAL PRIMARY KEY,
  admin_id   BIGINT,
  admin_name VARCHAR(120),
  action     VARCHAR(60) NOT NULL,
  target     VARCHAR(120),
  detail     TEXT,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_admin_action_time ON admin_action_ (created_at DESC);

-- Bloqueio de usuários pelo painel
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS blocked BOOLEAN DEFAULT FALSE;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS blocked_reason TEXT;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS created_at_ TIMESTAMP;
UPDATE user_ SET created_at_ = NOW() WHERE created_at_ IS NULL;

-- Avisos em massa
CREATE TABLE IF NOT EXISTS broadcast_ (
  id         BIGSERIAL PRIMARY KEY,
  audience   VARCHAR(20) NOT NULL,
  text       TEXT NOT NULL,
  sent_count INT NOT NULL DEFAULT 0,
  admin_name VARCHAR(120),
  created_at TIMESTAMP NOT NULL
);

-- Cupons e promoções
CREATE TABLE IF NOT EXISTS coupon_ (
  code         VARCHAR(40) PRIMARY KEY,
  description  VARCHAR(200),
  percent      NUMERIC(5, 2),
  amount       NUMERIC(15, 2),
  min_order    NUMERIC(15, 2) DEFAULT 0,
  valid_until  DATE,
  max_uses     INT,
  used         INT NOT NULL DEFAULT 0,
  one_per_user BOOLEAN NOT NULL DEFAULT TRUE,
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS coupon_use_ (
  code     VARCHAR(40) NOT NULL,
  user_id  BIGINT NOT NULL,
  order_id UUID NOT NULL,
  discount NUMERIC(15, 2) NOT NULL,
  used_at  TIMESTAMP NOT NULL
);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(40);

-- SOS: localização ao vivo, contato de confiança e áudio
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION;
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION;
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS loc_at TIMESTAMP;
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP;
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS acknowledged_by VARCHAR(120);
ALTER TABLE protection_session_ ADD COLUMN IF NOT EXISTS note TEXT;
CREATE TABLE IF NOT EXISTS protection_audio_ (
  id           UUID PRIMARY KEY,
  session_id   UUID NOT NULL,
  content_type VARCHAR(40) NOT NULL,
  data         BYTEA NOT NULL,
  created_at   TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_protection_audio_session ON protection_audio_ (session_id, created_at);
CREATE TABLE IF NOT EXISTS emergency_contact_ (
  user_id    BIGINT PRIMARY KEY,
  name       VARCHAR(120) NOT NULL,
  phone      VARCHAR(30) NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

-- Checagem automática de qualidade dos documentos
ALTER TABLE identity_verification_ ADD COLUMN IF NOT EXISTS quality_report TEXT;

-- Chegada (pontualidade e selo Top OPUS)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS reminded_24h BOOLEAN DEFAULT FALSE;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS reminded_1h BOOLEAN DEFAULT FALSE;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS package_purchase_id UUID;

-- Saúde (ficha, amostras do relógio e treinos)
CREATE TABLE IF NOT EXISTS health_profile_ (
  user_id        BIGINT PRIMARY KEY,
  weight_kg      NUMERIC(5, 1),
  height_cm      NUMERIC(5, 1),
  birth_year     INT,
  conditions     TEXT,
  medications    TEXT,
  injuries       TEXT,
  goals          TEXT,
  notes          TEXT,
  share_with_pro BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at     TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS health_sample_ (
  id        BIGSERIAL PRIMARY KEY,
  user_id   BIGINT NOT NULL,
  kind      VARCHAR(20) NOT NULL,
  value     DOUBLE PRECISION NOT NULL,
  at        TIMESTAMP NOT NULL,
  source    VARCHAR(60)
);
CREATE INDEX IF NOT EXISTS idx_health_sample_user ON health_sample_ (user_id, kind, at DESC);
CREATE TABLE IF NOT EXISTS workout_ (
  id          UUID PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  order_id    UUID,
  class_id    UUID,
  title       VARCHAR(120),
  started_at  TIMESTAMP NOT NULL,
  ended_at    TIMESTAMP NOT NULL,
  avg_hr      INT,
  max_hr      INT,
  calories    INT,
  steps       INT,
  zones       TEXT,
  summary     TEXT,
  created_at  TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_workout_user ON workout_ (user_id, started_at DESC);

-- Aulas em grupo
CREATE TABLE IF NOT EXISTS group_class_ (
  id              UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  title           VARCHAR(120) NOT NULL,
  modality        VARCHAR(40) NOT NULL,
  description     TEXT,
  level           VARCHAR(20),
  starts_at       TIMESTAMP NOT NULL,
  duration_min    INT NOT NULL,
  price           NUMERIC(15, 2) NOT NULL,
  min_students    INT NOT NULL,
  max_students    INT NOT NULL,
  status          VARCHAR(16) NOT NULL,
  started_at      TIMESTAMP,
  ended_at        TIMESTAMP,
  created_at      TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_group_class_time ON group_class_ (status, starts_at);
CREATE TABLE IF NOT EXISTS class_enrollment_ (
  id             UUID PRIMARY KEY,
  class_id       UUID NOT NULL,
  user_id        BIGINT NOT NULL,
  status         VARCHAR(16) NOT NULL,
  service_value  NUMERIC(15, 2) NOT NULL,
  client_total   NUMERIC(15, 2) NOT NULL,
  pix_payment_id VARCHAR(80),
  pix_copy_paste TEXT,
  pix_qr         TEXT,
  paid_at        TIMESTAMP,
  released       BOOLEAN NOT NULL DEFAULT FALSE,
  refunded       BOOLEAN NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_class_enrollment ON class_enrollment_ (class_id, user_id);
CREATE INDEX IF NOT EXISTS idx_class_enrollment_pix ON class_enrollment_ (pix_payment_id);

-- Sala ao vivo (presença e sinalização entre os celulares)
CREATE TABLE IF NOT EXISTS room_member_ (
  class_id  UUID NOT NULL,
  user_id   BIGINT NOT NULL,
  name      VARCHAR(120),
  is_host   BOOLEAN NOT NULL DEFAULT FALSE,
  joined_at TIMESTAMP NOT NULL,
  last_seen TIMESTAMP NOT NULL,
  bpm       INT,
  bpm_at    TIMESTAMP,
  PRIMARY KEY (class_id, user_id)
);
CREATE TABLE IF NOT EXISTS room_signal_ (
  id         BIGSERIAL PRIMARY KEY,
  class_id   UUID NOT NULL,
  from_user  BIGINT NOT NULL,
  to_user    BIGINT NOT NULL,
  kind       VARCHAR(12) NOT NULL,
  payload    TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_room_signal_to ON room_signal_ (class_id, to_user, id);

-- Pacotes de aulas
CREATE TABLE IF NOT EXISTS lesson_package_ (
  id              UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  title           VARCHAR(120) NOT NULL,
  sessions        INT NOT NULL,
  hours_each      INT NOT NULL DEFAULT 1,
  price           NUMERIC(15, 2) NOT NULL,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS package_purchase_ (
  id              UUID PRIMARY KEY,
  package_id      UUID NOT NULL,
  user_id         BIGINT NOT NULL,
  professional_id BIGINT NOT NULL,
  sessions_total  INT NOT NULL,
  sessions_used   INT NOT NULL DEFAULT 0,
  service_value   NUMERIC(15, 2) NOT NULL,
  client_total    NUMERIC(15, 2) NOT NULL,
  status          VARCHAR(16) NOT NULL,
  pix_payment_id  VARCHAR(80),
  pix_copy_paste  TEXT,
  pix_qr          TEXT,
  paid_at         TIMESTAMP,
  created_at      TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_package_purchase_user ON package_purchase_ (user_id, professional_id);
CREATE INDEX IF NOT EXISTS idx_package_purchase_pix ON package_purchase_ (pix_payment_id);

-- Indique e ganhe
CREATE TABLE IF NOT EXISTS referral_ (
  referred_id  BIGINT PRIMARY KEY,
  referrer_id  BIGINT NOT NULL,
  created_at   TIMESTAMP NOT NULL,
  rewarded_at  TIMESTAMP
);
