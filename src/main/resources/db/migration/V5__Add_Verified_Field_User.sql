ALTER TABLE user_ ADD COLUMN IF NOT EXISTS verified boolean DEFAULT false;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS document_url varchar(255);
UPDATE user_ SET verified = false WHERE verified IS NULL;
