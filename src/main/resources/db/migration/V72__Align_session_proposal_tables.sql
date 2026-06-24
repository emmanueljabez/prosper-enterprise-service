ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS booking_primary_goal TEXT,
    ADD COLUMN IF NOT EXISTS booking_already_tried TEXT,
    ADD COLUMN IF NOT EXISTS booking_success_looks_like TEXT,
    ADD COLUMN IF NOT EXISTS booking_context_document TEXT;

CREATE TABLE IF NOT EXISTS session_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

ALTER TABLE session_proposals
    ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS proposal_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS mentor_message TEXT,
    ADD COLUMN IF NOT EXISTS mentee_response TEXT,
    ADD COLUMN IF NOT EXISTS accepted_slot_id UUID,
    ADD COLUMN IF NOT EXISTS proposed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS responded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE session_proposals SET proposal_type = 'SINGLE_SLOT' WHERE proposal_type IS NULL;
UPDATE session_proposals SET status = 'PENDING_MENTEE_RESPONSE' WHERE status IS NULL;
UPDATE session_proposals SET proposed_at = NOW() WHERE proposed_at IS NULL;
UPDATE session_proposals SET created_at = NOW() WHERE created_at IS NULL;
UPDATE session_proposals SET version = 0 WHERE version IS NULL;

CREATE TABLE IF NOT EXISTS session_proposal_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

ALTER TABLE session_proposal_slots
    ADD COLUMN IF NOT EXISTS proposal_id UUID REFERENCES session_proposals(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS scheduled_start TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scheduled_end TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

UPDATE session_proposal_slots SET sort_order = 0 WHERE sort_order IS NULL;
UPDATE session_proposal_slots SET created_at = NOW() WHERE created_at IS NULL;

CREATE TABLE IF NOT EXISTS session_support_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

ALTER TABLE session_support_requests
    ADD COLUMN IF NOT EXISTS session_id UUID REFERENCES sessions(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS requester_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS requester_id UUID,
    ADD COLUMN IF NOT EXISTS message TEXT,
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE session_support_requests SET status = 'OPEN' WHERE status IS NULL;
UPDATE session_support_requests SET created_at = NOW() WHERE created_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_session_proposals_one_pending_per_session
    ON session_proposals(session_id)
    WHERE status = 'PENDING_MENTEE_RESPONSE';

CREATE INDEX IF NOT EXISTS idx_session_proposals_session_status
    ON session_proposals(session_id, status, proposed_at DESC);

CREATE INDEX IF NOT EXISTS idx_session_proposal_slots_proposal
    ON session_proposal_slots(proposal_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_session_support_requests_session
    ON session_support_requests(session_id, created_at DESC);
