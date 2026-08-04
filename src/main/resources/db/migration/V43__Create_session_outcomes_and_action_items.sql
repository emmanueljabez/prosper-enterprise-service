CREATE TABLE IF NOT EXISTS session_outcomes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    summary TEXT,
    reflection_prompt TEXT,
    mentor_private_notes TEXT,
    recorded_by_user_id UUID,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_session_outcomes_session UNIQUE (session_id)
);

CREATE INDEX IF NOT EXISTS idx_session_outcomes_session
    ON session_outcomes(session_id);

CREATE TABLE IF NOT EXISTS session_outcome_action_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_outcome_id UUID NOT NULL REFERENCES session_outcomes(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    owner_type VARCHAR(20) NOT NULL DEFAULT 'MENTEE',
    due_at TIMESTAMP WITH TIME ZONE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP,
    completed_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_outcome_action_items_outcome
    ON session_outcome_action_items(session_outcome_id, sort_order ASC);

CREATE INDEX IF NOT EXISTS idx_session_outcome_action_items_completion
    ON session_outcome_action_items(completed_at, due_at);

CREATE OR REPLACE FUNCTION update_session_outcomes_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_session_outcome_action_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_session_outcomes_updated_at ON session_outcomes;
CREATE TRIGGER trigger_session_outcomes_updated_at
    BEFORE UPDATE ON session_outcomes
    FOR EACH ROW
    EXECUTE FUNCTION update_session_outcomes_updated_at();

DROP TRIGGER IF EXISTS trigger_session_outcome_action_items_updated_at ON session_outcome_action_items;
CREATE TRIGGER trigger_session_outcome_action_items_updated_at
    BEFORE UPDATE ON session_outcome_action_items
    FOR EACH ROW
    EXECUTE FUNCTION update_session_outcome_action_items_updated_at();

COMMENT ON TABLE session_outcomes IS 'Structured mentor-recorded outcomes for completed sessions';
COMMENT ON TABLE session_outcome_action_items IS 'Action items generated from a completed mentorship session';
