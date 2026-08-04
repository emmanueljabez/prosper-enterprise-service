ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS company_program_id UUID REFERENCES company_programs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS company_program_participant_id UUID REFERENCES company_program_participants(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sessions_company_program
    ON sessions(company_program_id, scheduled_start DESC);

CREATE INDEX IF NOT EXISTS idx_sessions_company_program_participant
    ON sessions(company_program_participant_id, scheduled_start DESC);

COMMENT ON COLUMN sessions.company_program_id IS 'Optional company program context for corporate bookings';
COMMENT ON COLUMN sessions.company_program_participant_id IS 'Optional company program participant context for corporate bookings';
