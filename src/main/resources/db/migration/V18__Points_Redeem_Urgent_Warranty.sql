-- V18: desconto com pontos, pedido urgente e garantia de 30 dias

-- Desconto aplicado ao pedido (resgate de pontos). O valor do profissional não muda:
-- o desconto sai da parte da plataforma.
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(15, 2) DEFAULT 0;
-- Data em que o serviço foi concluído (conta o prazo da garantia)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS done_at TIMESTAMP;
-- Pedido nascido de um chamado urgente
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS urgent BOOLEAN DEFAULT FALSE;

-- Chamado urgente: vai para todos os profissionais próximos; o primeiro que aceitar leva
CREATE TABLE IF NOT EXISTS urgent_request_ (
  id               UUID PRIMARY KEY,
  customer_id      BIGINT NOT NULL,
  category_name    VARCHAR(120),
  description      TEXT,
  address          TEXT,
  latitude         DOUBLE PRECISION,
  longitude        DOUBLE PRECISION,
  status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  taken_by         BIGINT,
  created_at       TIMESTAMP,
  taken_at         TIMESTAMP,
  expires_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_urgent_status ON urgent_request_ (status);
CREATE INDEX IF NOT EXISTS idx_urgent_customer ON urgent_request_ (customer_id);

-- Garantia OPUS: o cliente aciona em até 30 dias após a conclusão
CREATE TABLE IF NOT EXISTS warranty_claim_ (
  id               UUID PRIMARY KEY,
  order_id         UUID NOT NULL,
  customer_id      BIGINT NOT NULL,
  professional_id  BIGINT,
  reason           TEXT,
  status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  professional_note TEXT,
  scheduled_for    VARCHAR(120),
  created_at       TIMESTAMP,
  updated_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_warranty_order ON warranty_claim_ (order_id);
CREATE INDEX IF NOT EXISTS idx_warranty_professional ON warranty_claim_ (professional_id);
