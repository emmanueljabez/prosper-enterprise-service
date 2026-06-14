CREATE TABLE IF NOT EXISTS consent_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES company_program_participants(id) ON DELETE CASCADE,
    consent_type VARCHAR(48) NOT NULL,
    status VARCHAR(16) NOT NULL,
    captured_by_user_id UUID,
    captured_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_consent_records_type CHECK (
        consent_type IN ('PROGRAM_PARTICIPATION', 'AGGREGATED_ANALYTICS', 'EMPLOYER_PROGRESS_VISIBILITY')
    ),
    CONSTRAINT chk_consent_records_status CHECK (
        status IN ('GRANTED', 'REVOKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_consent_records_participant_type_captured
    ON consent_records(participant_id, consent_type, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_consent_records_participant_captured
    ON consent_records(participant_id, captured_at DESC);

COMMENT ON TABLE consent_records IS 'Append-only participant consent decisions for corporate mentorship programs';

CREATE TABLE IF NOT EXISTS access_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID REFERENCES companies(id) ON DELETE SET NULL,
    company_program_id UUID REFERENCES company_programs(id) ON DELETE SET NULL,
    participant_id UUID REFERENCES company_program_participants(id) ON DELETE SET NULL,
    actor_id UUID,
    actor_role VARCHAR(64),
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID,
    action VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_access_audit_logs_action CHECK (
        action IN ('VIEW', 'UPDATE', 'REMATCH')
    )
);

CREATE INDEX IF NOT EXISTS idx_access_audit_logs_company_created
    ON access_audit_logs(company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_access_audit_logs_company_program_created
    ON access_audit_logs(company_program_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_access_audit_logs_participant_created
    ON access_audit_logs(participant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_access_audit_logs_actor_created
    ON access_audit_logs(actor_id, created_at DESC);

COMMENT ON TABLE access_audit_logs IS 'Auditable access trail for sensitive company-program participant and review resources';
