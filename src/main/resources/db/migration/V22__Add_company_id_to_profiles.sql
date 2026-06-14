-- Add company_id to profiles table
-- This allows linking user profiles to companies for corporate accounts

-- Add company_id column (nullable - not all users will belong to a company)
ALTER TABLE profiles
ADD COLUMN IF NOT EXISTS company_id UUID;

-- Add foreign key constraint to companies table
ALTER TABLE profiles
ADD CONSTRAINT fk_profiles_company
    FOREIGN KEY (company_id)
    REFERENCES companies(id)
    ON DELETE SET NULL;

-- Create index on company_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_profiles_company_id ON profiles(company_id);

-- Add comment to explain the column
COMMENT ON COLUMN profiles.company_id IS 'Reference to the company this user belongs to (NULL if not associated with any company)';
