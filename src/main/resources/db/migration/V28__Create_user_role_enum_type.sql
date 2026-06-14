-- Create the user_role enum type if it doesn't exist
DO $$ BEGIN
    CREATE TYPE user_role AS ENUM ('mentee', 'mentor', 'advisee', 'advisor', 'admin', 'company');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Add comment for documentation
COMMENT ON TYPE user_role IS 'Enum type for user roles in the system';
