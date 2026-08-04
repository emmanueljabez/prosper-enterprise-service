-- Create company_employee_whitelist table
CREATE TABLE IF NOT EXISTS company_employee_whitelist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    notes TEXT,
    added_by VARCHAR(255),
    invitation_token VARCHAR(255),
    invitation_token_expiry TIMESTAMP,
    invitation_sent BOOLEAN DEFAULT FALSE,
    invitation_accepted BOOLEAN DEFAULT FALSE,
    profile_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_company_email UNIQUE (company_id, email)
);

-- Create indexes for better query performance
CREATE INDEX idx_company_employee_whitelist_company_id ON company_employee_whitelist(company_id);
CREATE INDEX idx_company_employee_whitelist_email ON company_employee_whitelist(email);
CREATE INDEX idx_company_employee_whitelist_token ON company_employee_whitelist(invitation_token);
