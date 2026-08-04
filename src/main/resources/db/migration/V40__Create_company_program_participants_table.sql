CREATE TABLE IF NOT EXISTS company_program_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_program_id UUID NOT NULL REFERENCES company_programs(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ENROLLED',
    enrolled_by_user_id UUID,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_program_participant UNIQUE (company_program_id, profile_id),
    CONSTRAINT chk_company_program_participant_status
        CHECK (status IN ('ENROLLED', 'ACTIVE', 'COMPLETED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_company_program_participants_program_status
    ON company_program_participants(company_program_id, status, enrolled_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_program_participants_profile
    ON company_program_participants(profile_id, enrolled_at DESC);

CREATE OR REPLACE FUNCTION update_company_program_participants_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_program_participants_updated_at ON company_program_participants;
CREATE TRIGGER trigger_company_program_participants_updated_at
    BEFORE UPDATE ON company_program_participants
    FOR EACH ROW
    EXECUTE FUNCTION update_company_program_participants_updated_at();

COMMENT ON TABLE company_program_participants IS 'Employee enrollments into company mentorship programs';
COMMENT ON COLUMN company_program_participants.status IS 'Participant status: ENROLLED, ACTIVE, COMPLETED, WITHDRAWN';
