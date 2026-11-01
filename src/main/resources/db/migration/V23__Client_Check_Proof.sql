-- Entrega 4 · parte 1: o cliente confere o serviço, grava a prova e entrega o código ao profissional
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS service_mode VARCHAR(12) DEFAULT 'HOME';
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS check_items TEXT;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS client_checked_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS code_rotated_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS released_by VARCHAR(20);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pro_checklist TEXT;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS dispute_resolution TEXT;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS client_refund NUMERIC(12,2);
ALTER TABLE order_photo_ ADD COLUMN IF NOT EXISTS size_bytes INTEGER;
CREATE INDEX IF NOT EXISTS idx_order_dispute_open ON order_ (dispute_status, disputed_at);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS release_reminded BOOLEAN DEFAULT false;

-- Carteira do profissional: o pagamento liberado entra no saldo; o profissional saca no PIX quando quiser
CREATE TABLE IF NOT EXISTS wallet_entry_ (
  id UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  kind VARCHAR(12) NOT NULL,          -- CREDIT | WITHDRAW
  amount NUMERIC(12,2) NOT NULL,
  order_id UUID,
  status VARCHAR(12) NOT NULL,        -- DONE | PENDING | FAILED
  description VARCHAR(200),
  pix_key VARCHAR(140),
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_wallet_pro ON wallet_entry_ (professional_id, created_at);
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_credit_order ON wallet_entry_ (order_id, kind) WHERE kind = 'CREDIT';
