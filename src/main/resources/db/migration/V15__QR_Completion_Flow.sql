-- QR Token table for arrival and completion confirmation
CREATE TABLE qr_token_ (
    id           BIGSERIAL PRIMARY KEY,
    order_id     UUID         NOT NULL,
    code         VARCHAR(10)  NOT NULL UNIQUE,
    type         VARCHAR(30)  NOT NULL, -- ARRIVAL or COMPLETION
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at   TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_token_order_type ON qr_token_(order_id, type);
CREATE INDEX idx_qr_token_code ON qr_token_(code);

-- Track when professional requested completion (for 24h auto-confirm)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS requested_completion_at TIMESTAMP;
