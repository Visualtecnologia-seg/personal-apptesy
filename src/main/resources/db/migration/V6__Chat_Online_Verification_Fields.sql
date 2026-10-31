-- Campos de verificação de identidade (User)
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS doc_front_url TEXT;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS doc_back_url TEXT;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS doc_selfie_url TEXT;
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS verification_status VARCHAR(50) DEFAULT 'NONE';
ALTER TABLE user_ ADD COLUMN IF NOT EXISTS full_address TEXT;

-- Status online/offline do prestador
ALTER TABLE professional_ ADD COLUMN IF NOT EXISTS is_online BOOLEAN DEFAULT false;

-- Tabela de mensagens do chat
CREATE TABLE IF NOT EXISTS message_ (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    sender_id BIGINT,
    sender_name VARCHAR(255),
    sender_role VARCHAR(50),
    text TEXT,
    image_url TEXT,
    message_type VARCHAR(50) DEFAULT 'TEXT',
    quote_status VARCHAR(50),
    quote_amount NUMERIC(15, 2),
    quote_description TEXT,
    quote_hours INTEGER,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_message_order_id ON message_(order_id);
CREATE INDEX IF NOT EXISTS idx_message_created_at ON message_(created_at);
