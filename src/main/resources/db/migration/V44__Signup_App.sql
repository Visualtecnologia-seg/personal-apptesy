-- De qual app a pessoa se cadastrou (PRO = app do profissional). Serve para escolher o e-mail certo.
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS signup_app VARCHAR(12);
UPDATE user_ u SET signup_app = 'PRO' WHERE signup_app IS NULL AND (
  EXISTS (SELECT 1 FROM product__professional pp WHERE pp.professional_id = u.id)
  OR EXISTS (SELECT 1 FROM order_ o WHERE o.professional_id = u.id));
