-- V13: Featured flag, Fast Responder, Specialty History, Points system

-- Add featured and fast_responder flags to professional_
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS featured BOOLEAN DEFAULT FALSE;
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS fast_responder BOOLEAN DEFAULT FALSE;

-- Specialty history: tracks every change to the specialties field
CREATE TABLE IF NOT EXISTS specialty_history_ (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES user_ (id),
    specialties TEXT,
    changed_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Points: earned by clients after completing a service
CREATE TABLE IF NOT EXISTS points_ (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES user_ (id),
    amount     INTEGER NOT NULL,
    reason     VARCHAR(255),
    order_id   UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for fast balance queries
CREATE INDEX IF NOT EXISTS idx_points_user_id ON points_ (user_id);
CREATE INDEX IF NOT EXISTS idx_specialty_history_user_id ON specialty_history_ (user_id);
