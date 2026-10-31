-- Quote items as JSON + totals breakdown
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_items_json TEXT;
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_service_cost NUMERIC(15, 2);
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_materials_cost NUMERIC(15, 2);
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_total_cost NUMERIC(15, 2);

-- Counter offer (1 round of negotiation)
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS counter_offer_amount NUMERIC(15, 2);
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS counter_offer_note TEXT;
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS counter_offer_status VARCHAR(50);

-- Client checklist: JSON array of item IDs the client has already bought
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS client_checked_items TEXT;

-- New quote statuses: PENDING | NEGOTIATING | ACCEPTED | REJECTED
-- (quoteStatus already exists, just using new value NEGOTIATING)
