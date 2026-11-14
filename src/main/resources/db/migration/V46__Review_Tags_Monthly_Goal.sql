-- Elogios rápidos na avaliação (ex.: "Limpou tudo depois") e meta do mês do profissional
ALTER TABLE review_ ADD COLUMN IF NOT EXISTS tags TEXT;
ALTER TABLE professional_area_ ADD COLUMN IF NOT EXISTS monthly_goal NUMERIC(12,2);
