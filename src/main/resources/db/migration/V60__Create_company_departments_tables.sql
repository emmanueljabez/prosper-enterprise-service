CREATE TABLE IF NOT EXISTS company_departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_department_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_departments_company_name
    ON company_departments (company_id, LOWER(name));

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_departments_company_code
    ON company_departments (company_id, LOWER(code))
    WHERE code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_company_departments_company_created
    ON company_departments (company_id, created_at DESC);

CREATE TABLE IF NOT EXISTS company_department_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_department_id UUID NOT NULL REFERENCES company_departments(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_by_user_id UUID,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_department_members_department_profile
    ON company_department_members (company_department_id, profile_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_company_department_members_profile
    ON company_department_members (profile_id);

CREATE INDEX IF NOT EXISTS idx_company_department_members_department
    ON company_department_members (company_department_id, joined_at DESC);

CREATE OR REPLACE FUNCTION update_company_departments_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_departments_updated_at ON company_departments;
CREATE TRIGGER trigger_company_departments_updated_at
    BEFORE UPDATE ON company_departments
    FOR EACH ROW
    EXECUTE FUNCTION update_company_departments_updated_at();

CREATE OR REPLACE FUNCTION update_company_department_members_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_department_members_updated_at ON company_department_members;
CREATE TRIGGER trigger_company_department_members_updated_at
    BEFORE UPDATE ON company_department_members
    FOR EACH ROW
    EXECUTE FUNCTION update_company_department_members_updated_at();

COMMENT ON TABLE company_departments IS 'Company-managed department catalog for employee organization';
COMMENT ON TABLE company_department_members IS 'Employee assignment records for company departments';
