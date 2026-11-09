-- Tentativas erradas do código do cliente (impede adivinhar o código e liberar o pagamento)
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS code_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS code_attempts_total INT NOT NULL DEFAULT 0;
-- Estorno real no Asaas: guarda o id/estado devolvido
ALTER TABLE transaction_ ADD COLUMN IF NOT EXISTS refund_status VARCHAR(20);
ALTER TABLE transaction_ ADD COLUMN IF NOT EXISTS refund_error VARCHAR(300);
ALTER TABLE transaction_ ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;
