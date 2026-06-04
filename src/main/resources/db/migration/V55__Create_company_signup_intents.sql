CREATE TABLE company_signup_intents (
    id UUID PRIMARY KEY,
    token VARCHAR(120) NOT NULL UNIQUE,
    company_id UUID NOT NULL REFERENCES companies(id),
    company_registration_token VARCHAR(120) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    admin_first_name VARCHAR(120) NOT NULL,
    admin_last_name VARCHAR(120) NOT NULL,
    admin_phone_number VARCHAR(80) NOT NULL,
    target_plan_id UUID NULL REFERENCES subscription_plans(id),
    target_session_count INTEGER NULL,
    status VARCHAR(40) NOT NULL,
    linked_user_id UUID NULL,
    linked_profile_id UUID NULL,
    completed_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_company_signup_intents_token ON company_signup_intents(token);
CREATE INDEX idx_company_signup_intents_admin_email_status ON company_signup_intents(admin_email, status);
