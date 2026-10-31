-- V17: Fluxo de verificação de identidade (documento + selfies guiadas + análise manual)
CREATE TABLE IF NOT EXISTS identity_verification_ (
  id                UUID PRIMARY KEY,
  user_id           BIGINT NOT NULL,
  status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  doc_type          VARCHAR(20),
  doc_front         TEXT,
  doc_back          TEXT,
  selfie_neutral    TEXT,
  selfie_turn_a     TEXT,
  selfie_turn_b     TEXT,
  liveness_passed   BOOLEAN DEFAULT FALSE,
  liveness_info     VARCHAR(255),
  selfie_with_doc   TEXT,
  consent_version   VARCHAR(20),
  consent_at        TIMESTAMP,
  submitted_at      TIMESTAMP,
  reviewed_at       TIMESTAMP,
  reviewed_by       VARCHAR(255),
  rejection_reason  TEXT,
  rejected_steps    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_identity_verification_user ON identity_verification_ (user_id);
CREATE INDEX IF NOT EXISTS idx_identity_verification_status ON identity_verification_ (status);

ALTER TABLE user_ ADD COLUMN IF NOT EXISTS verification_reason TEXT;
