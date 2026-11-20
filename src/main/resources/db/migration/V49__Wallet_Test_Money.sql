-- Dinheiro do modo de teste (PIX simulado) nunca vira saque de dinheiro real
ALTER TABLE wallet_entry_ ADD COLUMN IF NOT EXISTS test BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE wallet_entry_ w SET test = TRUE
WHERE w.order_id IS NOT NULL AND EXISTS (
  SELECT 1 FROM transaction_ t WHERE t.order_id = w.order_id AND (
    t.asaas_payment_id LIKE 'TESTE-%'
    OR (t.asaas_payment_id LIKE 'PKG-%' AND EXISTS (
      SELECT 1 FROM order_ o JOIN package_purchase_ p ON p.id = o.package_purchase_id
      WHERE o.id = w.order_id AND (p.pix_payment_id IS NULL OR p.pix_payment_id LIKE 'TESTE-%')))));

-- saques feitos enquanto o pagamento era só de teste também são de teste
UPDATE wallet_entry_ w SET test = TRUE
WHERE w.kind = 'WITHDRAW' AND NOT EXISTS (
  SELECT 1 FROM wallet_entry_ c WHERE c.professional_id = w.professional_id AND c.kind IN ('CREDIT','STAGE') AND c.test = FALSE);
