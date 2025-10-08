-- Migration: Create tokens table
-- Description: Stores authentication tokens

-- Create tokens table
CREATE TABLE IF NOT EXISTS tokens (
    id BIGSERIAL PRIMARY KEY,
    token TEXT NOT NULL
);

-- Create index on token for faster lookups
CREATE INDEX IF NOT EXISTS idx_tokens_token ON tokens(token);

-- Add comments to table
COMMENT ON TABLE tokens IS 'Stores authentication and session tokens';
COMMENT ON COLUMN tokens.id IS 'Auto-incrementing primary key';
COMMENT ON COLUMN tokens.token IS 'Token value stored as text';
