-- Cobrança por hora ou pelo serviço todo (valor fechado), escolhida pelo profissional no orçamento
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_pricing VARCHAR(10);
ALTER TABLE message_ ADD COLUMN IF NOT EXISTS quote_rate NUMERIC(12,2);
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(10);
