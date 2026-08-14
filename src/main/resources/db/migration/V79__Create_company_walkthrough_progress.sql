CREATE TABLE company_user_walkthrough_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    version VARCHAR(80) NOT NULL,
    intro_dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_task_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    completed_tour_ids TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uniq_company_user_walkthrough_progress UNIQUE (company_id, profile_id, version)
);

CREATE INDEX idx_company_walkthrough_company
    ON company_user_walkthrough_progress(company_id);

CREATE INDEX idx_company_walkthrough_profile
    ON company_user_walkthrough_progress(profile_id);
