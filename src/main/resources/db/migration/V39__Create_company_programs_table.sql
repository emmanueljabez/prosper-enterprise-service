CREATE TABLE IF NOT EXISTS company_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    program_id UUID REFERENCES programs(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    objective TEXT,
    target_audience_description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    matching_mode VARCHAR(30) NOT NULL DEFAULT 'ADMIN_ASSIGN',
    visibility_policy_code VARCHAR(100),
    max_participants INTEGER,
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    created_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_program_status
        CHECK (status IN ('DRAFT', 'LIVE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT chk_company_program_matching_mode
        CHECK (matching_mode IN ('ADMIN_ASSIGN', 'EMPLOYEE_SELECT', 'SYSTEM_ASSIGN')),
    CONSTRAINT chk_company_program_max_participants
        CHECK (max_participants IS NULL OR max_participants > 0),
    CONSTRAINT chk_company_program_dates
        CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX IF NOT EXISTS idx_company_programs_company_status
    ON company_programs(company_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_programs_company_name
    ON company_programs(company_id, name);

CREATE OR REPLACE FUNCTION update_company_programs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_programs_updated_at ON company_programs;
CREATE TRIGGER trigger_company_programs_updated_at
    BEFORE UPDATE ON company_programs
    FOR EACH ROW
    EXECUTE FUNCTION update_company_programs_updated_at();

COMMENT ON TABLE company_programs IS 'Company-launched mentorship program runtime records';
COMMENT ON COLUMN company_programs.status IS 'Lifecycle status: DRAFT, LIVE, PAUSED, COMPLETED, CANCELLED, ARCHIVED';
COMMENT ON COLUMN company_programs.matching_mode IS 'Mentor assignment mode for the company program';
