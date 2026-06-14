CREATE TABLE IF NOT EXISTS company_program_match_workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES company_program_participants(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    selection_deadline_at TIMESTAMP,
    shortlist_generated_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(20),
    resolved_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_program_match_workspace_participant UNIQUE (participant_id),
    CONSTRAINT ck_company_program_match_workspace_status
        CHECK (status IN ('ADMIN_REVIEW', 'PENDING_EMPLOYEE_SELECTION', 'ASSIGNED', 'EXPIRED_NO_CANDIDATE', 'INACTIVE')),
    CONSTRAINT ck_company_program_match_workspace_resolved_by
        CHECK (resolved_by IS NULL OR resolved_by IN ('EMPLOYEE', 'ADMIN', 'SYSTEM'))
);

CREATE INDEX IF NOT EXISTS idx_company_program_match_workspaces_status_deadline
    ON company_program_match_workspaces(status, selection_deadline_at);

CREATE TABLE IF NOT EXISTS company_program_match_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES company_program_match_workspaces(id) ON DELETE CASCADE,
    mentor_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    rank_order INTEGER NOT NULL,
    recommendation_score NUMERIC(6, 2),
    recommendation_reason TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_program_match_option_workspace_mentor UNIQUE (workspace_id, mentor_id),
    CONSTRAINT ck_company_program_match_option_rank_positive CHECK (rank_order > 0)
);

CREATE INDEX IF NOT EXISTS idx_company_program_match_options_workspace_rank
    ON company_program_match_options(workspace_id, rank_order);

CREATE INDEX IF NOT EXISTS idx_company_program_match_options_mentor
    ON company_program_match_options(mentor_id);

CREATE OR REPLACE FUNCTION update_company_program_match_workspaces_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_program_match_workspaces_updated_at ON company_program_match_workspaces;
CREATE TRIGGER trigger_company_program_match_workspaces_updated_at
    BEFORE UPDATE ON company_program_match_workspaces
    FOR EACH ROW
    EXECUTE FUNCTION update_company_program_match_workspaces_updated_at();

CREATE OR REPLACE FUNCTION update_company_program_match_options_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_program_match_options_updated_at ON company_program_match_options;
CREATE TRIGGER trigger_company_program_match_options_updated_at
    BEFORE UPDATE ON company_program_match_options
    FOR EACH ROW
    EXECUTE FUNCTION update_company_program_match_options_updated_at();

COMMENT ON TABLE company_program_match_workspaces IS 'Matching workflow state for each company-program participant.';
COMMENT ON TABLE company_program_match_options IS 'Ranked mentor options that employees can choose from when employee selection is enabled.';
