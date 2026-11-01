-- Perfil novo
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS years_experience INT;
CREATE TABLE IF NOT EXISTS favorite_ (user_id BIGINT NOT NULL, professional_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, PRIMARY KEY (user_id, professional_id));
CREATE TABLE IF NOT EXISTS follow_ (user_id BIGINT NOT NULL, professional_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, PRIMARY KEY (user_id, professional_id));
CREATE INDEX IF NOT EXISTS idx_follow_pro ON follow_ (professional_id);
-- Social: trabalho feito, aula em vídeo, foto
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS kind VARCHAR(10) DEFAULT 'PHOTO';
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS order_id UUID;
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS title VARCHAR(140);
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS level VARCHAR(20);
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS modality VARCHAR(60);
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS hire_button BOOLEAN DEFAULT TRUE;
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS before_after BOOLEAN DEFAULT FALSE;
ALTER TABLE post_ ADD COLUMN IF NOT EXISTS views INT DEFAULT 0;
CREATE TABLE IF NOT EXISTS post_video_ (id UUID PRIMARY KEY, post_id UUID NOT NULL, content_type VARCHAR(40) NOT NULL, seconds INT, size_bytes BIGINT, data BYTEA NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_post_video_post ON post_video_ (post_id);
-- Lembrete 1 h antes
CREATE TABLE IF NOT EXISTS order_reminder_ (order_id UUID PRIMARY KEY, sent_at TIMESTAMP NOT NULL);
