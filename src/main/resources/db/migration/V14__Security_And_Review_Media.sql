-- V14: Login history, audit log, review media support

-- Login history: track every auth attempt
CREATE TABLE IF NOT EXISTS login_history_ (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES user_ (id),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    success    BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_login_history_user_id ON login_history_ (user_id);

-- Audit log: track critical admin actions
CREATE TABLE IF NOT EXISTS audit_log_ (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES user_ (id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id   VARCHAR(100),
    details     TEXT,
    ip_address  VARCHAR(50),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log_ (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id    ON audit_log_ (user_id);

-- Review media: support photos/videos in reviews
ALTER TABLE review_ ADD COLUMN IF NOT EXISTS media_type   VARCHAR(50);
ALTER TABLE review_ ADD COLUMN IF NOT EXISTS media_urls   TEXT;
ALTER TABLE review_ ADD COLUMN IF NOT EXISTS reviewer_role VARCHAR(20);
