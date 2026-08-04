-- Add 'company' value to user_role enum if it doesn't already exist
-- PostgreSQL doesn't allow adding duplicate enum values, so we need to check first

DO $$
BEGIN
    -- Check if 'company' value already exists in user_role enum
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        WHERE t.typname = 'user_role'
        AND e.enumlabel = 'company'
    ) THEN
        -- Add 'company' to the user_role enum
        ALTER TYPE user_role ADD VALUE 'company';
    END IF;
END $$;

-- Add a comment to document the enum values
COMMENT ON TYPE user_role IS 'User roles in the system: mentee, mentor, advisee, advisor, admin, company';
