-- Add hourlyRate to professional table
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS hourly_rate DOUBLE PRECISION DEFAULT 80.0;
UPDATE professional_ SET hourly_rate = price WHERE hourly_rate IS NULL;

-- Add hours and serviceAddress to order table
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS hours INTEGER DEFAULT 1;
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS service_address VARCHAR(500);
