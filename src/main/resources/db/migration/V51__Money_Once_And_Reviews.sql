-- Créditos avulsos na carteira entram uma única vez (extras, taxa de deslocamento, aulas em grupo)
ALTER TABLE wallet_entry_ ADD COLUMN IF NOT EXISTS ref VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_ref ON wallet_entry_ (ref) WHERE ref IS NOT NULL;

-- Avaliações: uma por pessoa em cada pedido (apaga repetidas, fica a primeira)
DELETE FROM review_ r USING review_ r2
 WHERE r.order_id = r2.order_id AND COALESCE(r.type, '') = COALESCE(r2.type, '') AND r.id > r2.id AND r.order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_order_type ON review_ (order_id, type) WHERE order_id IS NOT NULL;

-- Notas sempre de 1 a 5 (antes aceitava qualquer número)
UPDATE review_ SET rating = LEAST(GREATEST(rating, 1), 5) WHERE rating IS NOT NULL AND (rating < 1 OR rating > 5);
UPDATE review_ SET cat1 = LEAST(GREATEST(cat1, 1), 5) WHERE cat1 IS NOT NULL AND (cat1 < 1 OR cat1 > 5);
UPDATE review_ SET cat2 = LEAST(GREATEST(cat2, 1), 5) WHERE cat2 IS NOT NULL AND (cat2 < 1 OR cat2 > 5);
UPDATE review_ SET cat3 = LEAST(GREATEST(cat3, 1), 5) WHERE cat3 IS NOT NULL AND (cat3 < 1 OR cat3 > 5);
UPDATE review_ SET cat4 = LEAST(GREATEST(cat4, 1), 5) WHERE cat4 IS NOT NULL AND (cat4 < 1 OR cat4 > 5);
UPDATE professional_ p SET rating = x.avg
  FROM (SELECT u.professional_id AS pid, AVG(r.rating) AS avg FROM review_ r JOIN user_ u ON u.id = r.professional_id
        WHERE r.type = 'CLIENT_TO_PRO' AND r.rating IS NOT NULL GROUP BY u.professional_id) x
 WHERE p.id = x.pid AND (p.rating < 0 OR p.rating > 5);
UPDATE professional_ SET rating = LEAST(GREATEST(rating, 0), 5) WHERE rating < 0 OR rating > 5;

-- Disputa: o sistema não decide sozinho (2 = esperar a equipe)
UPDATE platform_setting_ SET value = '2' WHERE key = 'dispute_timeout_refund';
