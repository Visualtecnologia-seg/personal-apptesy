-- Entrega 4 · parte 3: materiais no orçamento, lojas parceiras, seguros (vitrine) e contatos de parceiros
CREATE TABLE IF NOT EXISTS order_material_ (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL,
  name VARCHAR(140) NOT NULL,
  qty NUMERIC(10,2) NOT NULL DEFAULT 1,
  unit VARCHAR(20),
  price NUMERIC(12,2) NOT NULL DEFAULT 0,
  buyer VARCHAR(8) NOT NULL DEFAULT 'PRO',     -- CLIENT | PRO | HAVE
  receipt BYTEA,
  receipt_type VARCHAR(40),
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_material_order ON order_material_ (order_id);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS materials_status VARCHAR(12);   -- SENT | ACCEPTED
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS materials_extra_id UUID;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS warranty_days INT;

CREATE TABLE IF NOT EXISTS partner_store_ (
  id UUID PRIMARY KEY,
  name VARCHAR(140) NOT NULL,
  category VARCHAR(80),
  whatsapp VARCHAR(30),
  address VARCHAR(255),
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  coupon_code VARCHAR(30),
  coupon_text VARCHAR(140),
  hours VARCHAR(80),
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS insurance_plan_ (
  id UUID PRIMARY KEY,
  audience VARCHAR(8) NOT NULL,      -- PRO | CLIENT
  title VARCHAR(120) NOT NULL,
  kind VARCHAR(60),                  -- Por serviço / Mensal
  partner VARCHAR(120),
  coverage TEXT,                     -- um item por linha
  price_text VARCHAR(60),
  link VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT true,
  position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS insurance_interest_ (
  user_id BIGINT NOT NULL,
  plan_key VARCHAR(80) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, plan_key)
);
CREATE TABLE IF NOT EXISTS partner_lead_ (
  id UUID PRIMARY KEY,
  kind VARCHAR(12) NOT NULL,         -- STORE | INSURER
  company VARCHAR(140) NOT NULL,
  contact VARCHAR(140),
  phone VARCHAR(40),
  email VARCHAR(140),
  message TEXT,
  created_at TIMESTAMP NOT NULL
);
