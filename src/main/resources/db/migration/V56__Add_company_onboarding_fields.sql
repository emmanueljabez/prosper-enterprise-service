ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS industry VARCHAR(255),
    ADD COLUMN IF NOT EXISTS company_size_band VARCHAR(80),
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(120),
    ADD COLUMN IF NOT EXISTS mentorship_objective TEXT,
    ADD COLUMN IF NOT EXISTS target_audience_description TEXT,
    ADD COLUMN IF NOT EXISTS program_design_preference VARCHAR(80),
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;
