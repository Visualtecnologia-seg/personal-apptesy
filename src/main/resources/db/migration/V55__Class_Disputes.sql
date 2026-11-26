-- Reclamação de aula em grupo: o dinheiro da vaga fica no cofre até a equipe decidir
ALTER TABLE class_enrollment_ ADD COLUMN IF NOT EXISTS dispute_status VARCHAR(20);
ALTER TABLE class_enrollment_ ADD COLUMN IF NOT EXISTS dispute_reason TEXT;
ALTER TABLE class_enrollment_ ADD COLUMN IF NOT EXISTS disputed_at TIMESTAMP;
ALTER TABLE class_enrollment_ ADD COLUMN IF NOT EXISTS dispute_note TEXT;
