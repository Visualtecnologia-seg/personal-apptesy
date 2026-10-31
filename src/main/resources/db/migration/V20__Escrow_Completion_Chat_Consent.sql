-- V20: taxa embutida, conclusão com código, fotos do serviço, mensagens lidas e aceite do guia de proteção

-- Valor do serviço que vai para o profissional (total_cost passa a ser o que o cliente paga: serviço + taxa)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS service_value NUMERIC(15, 2);
UPDATE order_ SET service_value = ROUND(total_cost / 1.05, 2) WHERE service_value IS NULL AND total_cost IS NOT NULL;

-- Código de 6 números mostrado pelo profissional ao terminar
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS finish_code VARCHAR(6);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS finish_code_expires_at TIMESTAMP;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS points_awarded BOOLEAN DEFAULT FALSE;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS payment_released_at TIMESTAMP;

-- Fotos do serviço (antes/depois) enviadas pelo profissional
CREATE TABLE IF NOT EXISTS order_photo_ (
  id           UUID PRIMARY KEY,
  order_id     UUID NOT NULL,
  kind         VARCHAR(10) NOT NULL,
  content_type VARCHAR(40) NOT NULL,
  data         BYTEA NOT NULL,
  created_at   TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_photo_order ON order_photo_ (order_id);

-- Última leitura do chat por pessoa (contador de não lidas)
CREATE TABLE IF NOT EXISTS chat_read_ (
  order_id     UUID NOT NULL,
  user_id      BIGINT NOT NULL,
  last_read_at TIMESTAMP NOT NULL,
  PRIMARY KEY (order_id, user_id)
);

-- Aceite do guia de proteção (cada versão do texto precisa de um aceite)
CREATE TABLE IF NOT EXISTS protection_consent_ (
  user_id     BIGINT NOT NULL,
  version     INT NOT NULL,
  accepted_at TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, version)
);
