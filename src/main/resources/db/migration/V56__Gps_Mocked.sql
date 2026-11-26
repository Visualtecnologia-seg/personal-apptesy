-- GPS falso: o celular do profissional avisou que a posição veio de um app de localização simulada
ALTER TABLE order_ ADD COLUMN IF NOT EXISTS pro_gps_mocked BOOLEAN DEFAULT FALSE;
