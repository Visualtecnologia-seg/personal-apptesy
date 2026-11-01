-- Entrega 4 · parte 4: ferramentas do profissional
CREATE TABLE IF NOT EXISTS order_refusal_ (order_id UUID NOT NULL, professional_id BIGINT NOT NULL, reason VARCHAR(80) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_refusal_pro ON order_refusal_ (professional_id);
CREATE TABLE IF NOT EXISTS pro_blocked_client_ (professional_id BIGINT NOT NULL, client_id BIGINT NOT NULL, reason VARCHAR(140), created_at TIMESTAMP NOT NULL, PRIMARY KEY (professional_id, client_id));
CREATE TABLE IF NOT EXISTS review_reply_ (review_id BIGINT PRIMARY KEY, professional_id BIGINT NOT NULL, text VARCHAR(600) NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS pro_certificate_ (id UUID PRIMARY KEY, professional_id BIGINT NOT NULL, title VARCHAR(140) NOT NULL, number VARCHAR(60), photo BYTEA, photo_type VARCHAR(40),
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING', note VARCHAR(200), created_at TIMESTAMP NOT NULL, reviewed_at TIMESTAMP);
CREATE TABLE IF NOT EXISTS pro_boost_ (id UUID PRIMARY KEY, professional_id BIGINT NOT NULL, days INT NOT NULL, price NUMERIC(10,2) NOT NULL, status VARCHAR(12) NOT NULL,
  pix_payment_id VARCHAR(80), pix_copy_paste TEXT, pix_qr TEXT, starts_at TIMESTAMP, ends_at TIMESTAMP, created_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_boost_active ON pro_boost_ (status, ends_at);
CREATE TABLE IF NOT EXISTS pro_expense_ (id UUID PRIMARY KEY, professional_id BIGINT NOT NULL, description VARCHAR(140) NOT NULL, amount NUMERIC(12,2) NOT NULL, spent_on DATE NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE TABLE IF NOT EXISTS pro_course_ (professional_id BIGINT NOT NULL, course_key VARCHAR(40) NOT NULL, done_at TIMESTAMP NOT NULL, PRIMARY KEY (professional_id, course_key));
CREATE TABLE IF NOT EXISTS workout_plan_ (id UUID PRIMARY KEY, professional_id BIGINT NOT NULL, client_id BIGINT NOT NULL, title VARCHAR(140) NOT NULL, items TEXT NOT NULL, updated_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_workout_plan_client ON workout_plan_ (client_id);
CREATE TABLE IF NOT EXISTS student_measure_ (id UUID PRIMARY KEY, professional_id BIGINT NOT NULL, client_id BIGINT NOT NULL, weight NUMERIC(6,2), waist NUMERIC(6,2), body_fat NUMERIC(5,2), note VARCHAR(200), measured_on DATE NOT NULL, created_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS idx_measure_client ON student_measure_ (client_id, measured_on);
ALTER TABLE lesson_package_ ADD COLUMN IF NOT EXISTS monthly BOOLEAN DEFAULT false;
ALTER TABLE package_purchase_ ADD COLUMN IF NOT EXISTS renew_notified BOOLEAN DEFAULT false;
CREATE TABLE IF NOT EXISTS weekly_summary_sent_ (professional_id BIGINT NOT NULL, week_start DATE NOT NULL, PRIMARY KEY (professional_id, week_start));
