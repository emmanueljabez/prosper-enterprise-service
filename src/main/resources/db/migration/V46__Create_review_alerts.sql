CREATE TABLE IF NOT EXISTS review_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_key VARCHAR(190) NOT NULL,
    review_cycle_id UUID NOT NULL REFERENCES review_cycles(id) ON DELETE CASCADE,
    review_request_id UUID NOT NULL REFERENCES review_requests(id) ON DELETE CASCADE,
    company_program_id UUID REFERENCES company_programs(id) ON DELETE SET NULL,
    participant_id UUID REFERENCES company_program_participants(id) ON DELETE SET NULL,
    mentor_assignment_id UUID REFERENCES company_program_mentor_assignments(id) ON DELETE SET NULL,
    alert_type VARCHAR(48) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    question_code VARCHAR(80),
    score_value NUMERIC(4, 2),
    boolean_value BOOLEAN,
    details TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_review_alert_key UNIQUE (alert_key),
    CONSTRAINT chk_review_alert_type CHECK (alert_type IN ('LOW_MENTOR_SCORE', 'LOW_MENTEE_SCORE', 'LOW_FIT_SCORE', 'DO_NOT_CONTINUE')),
    CONSTRAINT chk_review_alert_severity CHECK (severity IN ('MEDIUM', 'HIGH')),
    CONSTRAINT chk_review_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_review_alerts_status_severity
    ON review_alerts(status, severity, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_alerts_company_program
    ON review_alerts(company_program_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_alerts_participant
    ON review_alerts(participant_id, status, created_at DESC);

CREATE OR REPLACE FUNCTION update_review_alerts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_review_alerts_updated_at ON review_alerts;
CREATE TRIGGER trigger_review_alerts_updated_at
    BEFORE UPDATE ON review_alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_review_alerts_updated_at();

COMMENT ON TABLE review_alerts IS 'Operational alerts raised from revealed or partially revealed review cycles';
