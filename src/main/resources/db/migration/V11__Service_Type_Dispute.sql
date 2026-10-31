-- Service type: does this product require a quote negotiation?
-- false = fixed price (boxing lessons, yoga, etc.) → pay immediately after confirmation
-- true  = variable price (plumbing, electrical, etc.) → negotiate via chat first
ALTER TABLE product_ ADD COLUMN IF NOT EXISTS requires_quote BOOLEAN DEFAULT FALSE;

-- Dispute system: client can open a dispute after service is DONE
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS dispute_reason TEXT;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS dispute_status VARCHAR(50);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMP;

-- Cancellation tracking
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(50);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
