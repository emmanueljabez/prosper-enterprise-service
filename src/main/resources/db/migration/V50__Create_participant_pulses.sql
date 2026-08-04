CREATE TABLE IF NOT EXISTS participant_pulses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES company_program_participants(id) ON DELETE CASCADE,
    session_id UUID REFERENCES sessions(id) ON DELETE SET NULL,
    pulse_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    confidence_score INTEGER,
    satisfaction_score INTEGER,
    goal_clarity_score INTEGER,
    free_text_feedback TEXT,
    sent_at TIMESTAMP,
    expires_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_participant_pulses_participant_type UNIQUE (participant_id, pulse_type),
    CONSTRAINT chk_participant_pulses_type CHECK (
        pulse_type IN ('BASELINE', 'MIDPOINT', 'PROGRAM_END', 'D30', 'D60', 'D90')
    ),
    CONSTRAINT chk_participant_pulses_status CHECK (
        status IN ('PENDING', 'COMPLETED', 'EXPIRED')
    ),
    CONSTRAINT chk_participant_pulse_confidence CHECK (
        confidence_score IS NULL OR confidence_score BETWEEN 1 AND 5
    ),
    CONSTRAINT chk_participant_pulse_satisfaction CHECK (
        satisfaction_score IS NULL OR satisfaction_score BETWEEN 1 AND 5
    ),
    CONSTRAINT chk_participant_pulse_goal_clarity CHECK (
        goal_clarity_score IS NULL OR goal_clarity_score BETWEEN 1 AND 5
    )
);

CREATE INDEX IF NOT EXISTS idx_participant_pulses_participant_status
    ON participant_pulses(participant_id, status);

CREATE INDEX IF NOT EXISTS idx_participant_pulses_type_status
    ON participant_pulses(pulse_type, status);

CREATE INDEX IF NOT EXISTS idx_participant_pulses_sent_at
    ON participant_pulses(sent_at);

CREATE OR REPLACE FUNCTION update_participant_pulses_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_participant_pulses_updated_at ON participant_pulses;
CREATE TRIGGER trigger_participant_pulses_updated_at
    BEFORE UPDATE ON participant_pulses
    FOR EACH ROW
    EXECUTE FUNCTION update_participant_pulses_updated_at();

COMMENT ON TABLE participant_pulses IS 'Lightweight WhatsApp-first baseline and end-of-program pulse checkpoints for company-program employees';
