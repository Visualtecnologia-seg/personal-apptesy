-- Login com Apple (iPhone)
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS apple_sub VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_apple_sub ON user_ (apple_sub) WHERE apple_sub IS NOT NULL;
