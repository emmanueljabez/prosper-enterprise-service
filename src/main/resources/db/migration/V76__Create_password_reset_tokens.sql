CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_profile_id
    ON password_reset_tokens(profile_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_email
    ON password_reset_tokens(email);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token_hash
    ON password_reset_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at
    ON password_reset_tokens(expires_at);

COMMENT ON TABLE password_reset_tokens IS 'Backend-owned password reset tokens for branded reset emails.';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'SHA-256 hash of the one-time reset token sent to the user.';
COMMENT ON COLUMN password_reset_tokens.expires_at IS 'Timestamp after which the reset token is no longer valid.';
COMMENT ON COLUMN password_reset_tokens.used_at IS 'Timestamp set when the token is consumed.';
