CREATE TABLE IF NOT EXISTS personal_session_credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    source_session_id UUID REFERENCES sessions(id) ON DELETE SET NULL,
    source_payment_id UUID REFERENCES payments(id) ON DELETE SET NULL,
    consumed_session_id UUID REFERENCES sessions(id) ON DELETE SET NULL,
    credit_reason VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    consumed_at TIMESTAMP,
    CONSTRAINT chk_personal_session_credits_reason CHECK (
        credit_reason IN ('MENTOR_DECLINED_PAID_BOOKING')
    ),
    CONSTRAINT chk_personal_session_credits_status CHECK (
        status IN ('AVAILABLE', 'CONSUMED')
    )
);

CREATE INDEX IF NOT EXISTS idx_personal_session_credits_profile_status_created
    ON personal_session_credits(profile_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_personal_session_credits_source_payment
    ON personal_session_credits(source_payment_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_personal_session_credits_source_session_reason
    ON personal_session_credits(source_session_id, credit_reason)
    WHERE source_session_id IS NOT NULL;
