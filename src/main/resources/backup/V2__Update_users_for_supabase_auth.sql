-- Update users table to support Supabase Auth integration

-- Add new columns for Supabase integration
ALTER TABLE users 
ADD COLUMN supabase_user_id VARCHAR(36) UNIQUE,
ADD COLUMN phone VARCHAR(20),
ADD COLUMN avatar_url VARCHAR(500),
ADD COLUMN bio VARCHAR(1000),
ADD COLUMN last_login_at TIMESTAMP;

-- Make first_name and last_name nullable since they might come from Supabase metadata
ALTER TABLE users 
ALTER COLUMN first_name DROP NOT NULL,
ALTER COLUMN last_name DROP NOT NULL;

-- Make password nullable since authentication is handled by Supabase
ALTER TABLE users 
ALTER COLUMN password DROP NOT NULL;

-- Create index for Supabase user ID for faster lookups
CREATE INDEX idx_users_supabase_user_id ON users(supabase_user_id);

-- Create index for last login tracking
CREATE INDEX idx_users_last_login_at ON users(last_login_at);

-- Update the trigger function to handle the new columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';



