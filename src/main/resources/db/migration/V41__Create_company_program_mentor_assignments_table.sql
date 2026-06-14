CREATE TABLE IF NOT EXISTS company_program_mentor_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES company_program_participants(id) ON DELETE CASCADE,
    mentor_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    assigned_by_user_id UUID,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_program_mentor_assignment_participant UNIQUE (participant_id)
);

CREATE INDEX IF NOT EXISTS idx_company_program_mentor_assignments_participant
    ON company_program_mentor_assignments(participant_id);

CREATE INDEX IF NOT EXISTS idx_company_program_mentor_assignments_mentor
    ON company_program_mentor_assignments(mentor_id, assigned_at DESC);

CREATE OR REPLACE FUNCTION update_company_program_mentor_assignments_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_program_mentor_assignments_updated_at ON company_program_mentor_assignments;
CREATE TRIGGER trigger_company_program_mentor_assignments_updated_at
    BEFORE UPDATE ON company_program_mentor_assignments
    FOR EACH ROW
    EXECUTE FUNCTION update_company_program_mentor_assignments_updated_at();

COMMENT ON TABLE company_program_mentor_assignments IS 'Mentor assignments for company program participants';
