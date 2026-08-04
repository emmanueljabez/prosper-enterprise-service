CREATE TABLE IF NOT EXISTS review_cycles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(32) NOT NULL,
    session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    mentor_assignment_id UUID REFERENCES company_program_mentor_assignments(id) ON DELETE SET NULL,
    company_program_id UUID REFERENCES company_programs(id) ON DELETE SET NULL,
    participant_id UUID REFERENCES company_program_participants(id) ON DELETE SET NULL,
    mentor_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    mentee_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    status VARCHAR(32) NOT NULL,
    opened_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revealed_at TIMESTAMP WITHOUT TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_cycle_type CHECK (type IN ('SESSION', 'FIT')),
    CONSTRAINT chk_review_cycle_status CHECK (status IN ('OPEN', 'PARTIALLY_SUBMITTED', 'REVEALED', 'EXPIRED_PARTIAL', 'EXPIRED_EMPTY', 'CANCELLED')),
    CONSTRAINT chk_review_cycle_expiry CHECK (expires_at >= opened_at),
    CONSTRAINT uk_review_cycle_session_type UNIQUE (session_id, type)
);

CREATE INDEX IF NOT EXISTS idx_review_cycles_status_expiry
    ON review_cycles(status, expires_at ASC);

CREATE INDEX IF NOT EXISTS idx_review_cycles_participant
    ON review_cycles(participant_id, type, opened_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_cycles_assignment
    ON review_cycles(mentor_assignment_id, type, opened_at DESC);

CREATE TABLE IF NOT EXISTS review_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_cycle_id UUID NOT NULL REFERENCES review_cycles(id) ON DELETE CASCADE,
    reviewer_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    reviewer_role VARCHAR(16) NOT NULL,
    target_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    target_role VARCHAR(16) NOT NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'WHATSAPP',
    status VARCHAR(32) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    provider_message_id VARCHAR(255),
    sent_at TIMESTAMP WITHOUT TIME ZONE,
    submitted_at TIMESTAMP WITHOUT TIME ZONE,
    last_reminder_at TIMESTAMP WITHOUT TIME ZONE,
    last_outbound_at TIMESTAMP WITHOUT TIME ZONE,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_request_reviewer_role CHECK (reviewer_role IN ('MENTOR', 'MENTEE')),
    CONSTRAINT chk_review_request_target_role CHECK (target_role IN ('MENTOR', 'MENTEE')),
    CONSTRAINT chk_review_request_channel CHECK (channel IN ('WHATSAPP')),
    CONSTRAINT chk_review_request_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERY_FAILED', 'SUBMITTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT uk_review_request_cycle_reviewer UNIQUE (review_cycle_id, reviewer_profile_id)
);

CREATE INDEX IF NOT EXISTS idx_review_requests_status
    ON review_requests(status, sent_at ASC, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_review_requests_reviewer
    ON review_requests(reviewer_profile_id, status, created_at DESC);

CREATE OR REPLACE FUNCTION update_review_cycles_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_review_requests_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_review_cycles_updated_at ON review_cycles;
CREATE TRIGGER trigger_review_cycles_updated_at
    BEFORE UPDATE ON review_cycles
    FOR EACH ROW
    EXECUTE FUNCTION update_review_cycles_updated_at();

DROP TRIGGER IF EXISTS trigger_review_requests_updated_at ON review_requests;
CREATE TRIGGER trigger_review_requests_updated_at
    BEFORE UPDATE ON review_requests
    FOR EACH ROW
    EXECUTE FUNCTION update_review_requests_updated_at();

COMMENT ON TABLE review_cycles IS 'Review cycles opened after sessions or milestone checkpoints such as fit reviews';
COMMENT ON TABLE review_requests IS 'Per-side review requests delivered over WhatsApp for a review cycle';
