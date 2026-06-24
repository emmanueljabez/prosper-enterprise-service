ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS booking_primary_goal TEXT,
    ADD COLUMN IF NOT EXISTS booking_already_tried TEXT,
    ADD COLUMN IF NOT EXISTS booking_success_looks_like TEXT,
    ADD COLUMN IF NOT EXISTS booking_context_document TEXT;

CREATE TABLE IF NOT EXISTS session_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    proposal_type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    mentor_message TEXT,
    mentee_response TEXT,
    accepted_slot_id UUID,
    proposed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP,
    expires_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT chk_session_proposals_type CHECK (proposal_type IN ('SINGLE_SLOT', 'MULTIPLE_SLOTS')),
    CONSTRAINT chk_session_proposals_status CHECK (status IN ('PENDING_MENTEE_RESPONSE', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE IF NOT EXISTS session_proposal_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id UUID NOT NULL REFERENCES session_proposals(id) ON DELETE CASCADE,
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS session_support_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    requester_type VARCHAR(20) NOT NULL,
    requester_id UUID NOT NULL,
    message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT chk_session_support_requester_type CHECK (requester_type IN ('MENTOR', 'MENTEE')),
    CONSTRAINT chk_session_support_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_proposals_one_pending_per_session
    ON session_proposals(session_id)
    WHERE status = 'PENDING_MENTEE_RESPONSE';

CREATE INDEX IF NOT EXISTS idx_session_proposals_session_status
    ON session_proposals(session_id, status, proposed_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_proposal_slots_proposal
    ON session_proposal_slots(proposal_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_session_support_requests_session
    ON session_support_requests(session_id, created_at DESC);
