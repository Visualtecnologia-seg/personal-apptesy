-- Entrega 4 · parte 2: onde o profissional atende (raio, bairros, dias da semana, modo viagem) e local do atendimento
CREATE TABLE IF NOT EXISTS professional_area_ (
  professional_id BIGINT PRIMARY KEY,
  mode VARCHAR(16) NOT NULL DEFAULT 'RADIUS',      -- RADIUS | NEIGHBORHOODS
  center_lat DOUBLE PRECISION,
  center_lng DOUBLE PRECISION,
  center_label VARCHAR(160),
  radius_km NUMERIC(6,1) DEFAULT 10,
  neighborhoods TEXT,                               -- um por linha
  service_modes VARCHAR(60) DEFAULT 'HOME',          -- HOME,PRO_PLACE,ONLINE
  place_address VARCHAR(255),
  place_lat DOUBLE PRECISION,
  place_lng DOUBLE PRECISION,
  updated_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS professional_area_day_ (
  id UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  weekday INTEGER NOT NULL,                          -- 1 = segunda ... 7 = domingo
  label VARCHAR(160) NOT NULL,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  radius_km NUMERIC(6,1) DEFAULT 10
);
CREATE INDEX IF NOT EXISTS idx_area_day_pro ON professional_area_day_ (professional_id);
CREATE TABLE IF NOT EXISTS professional_trip_ (
  id UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  city VARCHAR(160) NOT NULL,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  radius_km NUMERIC(6,1) DEFAULT 25,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  hide_home BOOLEAN DEFAULT true,
  note VARCHAR(200),
  created_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_trip_dates ON professional_trip_ (start_date, end_date);
-- Agenda: horários de atendimento e bloqueios (férias)
CREATE TABLE IF NOT EXISTS professional_hours_ (
  id UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  weekday INTEGER NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_hours_pro ON professional_hours_ (professional_id);
CREATE TABLE IF NOT EXISTS professional_block_ (
  id UUID PRIMARY KEY,
  professional_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(120)
);
ALTER TABLE professional_area_ ADD COLUMN IF NOT EXISTS weekly_goal NUMERIC(12,2);
