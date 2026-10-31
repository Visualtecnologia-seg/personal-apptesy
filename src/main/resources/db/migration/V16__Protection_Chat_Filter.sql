CREATE TABLE IF NOT EXISTS protection_session_ (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL,
  user_id BIGINT NOT NULL,
  level INT NOT NULL DEFAULT 1,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  started_at TIMESTAMP NOT NULL DEFAULT NOW(),
  stopped_at TIMESTAMP,
  escalated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS blocked_attempt_ (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL,
  sender_id BIGINT NOT NULL,
  sender_role VARCHAR(20),
  matched_pattern VARCHAR(200),
  original_text TEXT,
  attempt_number INT NOT NULL DEFAULT 1,
  blocked_at TIMESTAMP NOT NULL DEFAULT NOW()
);
