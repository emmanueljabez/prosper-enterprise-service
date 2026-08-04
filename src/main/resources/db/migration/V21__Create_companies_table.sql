-- Create companies table
-- This table stores organization/company information for corporate accounts

CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    email_address VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    registration_token VARCHAR(255),
    registration_token_expiry TIMESTAMP,
    registration_completed BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on email_address for faster lookups
CREATE INDEX IF NOT EXISTS idx_companies_email ON companies(email_address);

-- Create index on registration_token for faster token validation
CREATE INDEX IF NOT EXISTS idx_companies_registration_token ON companies(registration_token);

-- Create index on is_active for filtering active companies
CREATE INDEX IF NOT EXISTS idx_companies_is_active ON companies(is_active);

-- Create index on registration_completed for filtering
CREATE INDEX IF NOT EXISTS idx_companies_registration_completed ON companies(registration_completed);

-- Add comments to explain the table and columns
COMMENT ON TABLE companies IS 'Stores organization/company information for corporate accounts in the mentorship platform';
COMMENT ON COLUMN companies.id IS 'Unique identifier for the company';
COMMENT ON COLUMN companies.name IS 'Company name';
COMMENT ON COLUMN companies.email_address IS 'Primary contact email for the company (must be unique)';
COMMENT ON COLUMN companies.phone_number IS 'Primary contact phone number';
COMMENT ON COLUMN companies.logo_url IS 'URL to company logo image';
COMMENT ON COLUMN companies.is_active IS 'Whether the company account is active';
COMMENT ON COLUMN companies.registration_token IS 'Token used for completing company registration (sent via email)';
COMMENT ON COLUMN companies.registration_token_expiry IS 'Expiration timestamp for the registration token';
COMMENT ON COLUMN companies.registration_completed IS 'Whether the company has completed the registration process';
COMMENT ON COLUMN companies.created_at IS 'Timestamp when the company was created';
COMMENT ON COLUMN companies.updated_at IS 'Timestamp when the company was last updated';
