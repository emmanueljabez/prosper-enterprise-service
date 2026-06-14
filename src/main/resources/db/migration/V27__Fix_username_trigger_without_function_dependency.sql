-- Migration to fix the handle_new_user trigger to generate username inline
-- This removes dependency on generate_username_from_name function which may not exist

-- Drop existing trigger and function
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();

-- Create or replace the function that handles new user creation
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    user_role TEXT;
    user_username TEXT;
    user_first_name TEXT;
    user_last_name TEXT;
    generated_username TEXT;
BEGIN
    -- Extract role from user metadata (default to 'mentee' if not provided)
    user_role := COALESCE(
        NEW.raw_user_meta_data->>'role',
        NEW.raw_app_meta_data->>'role',
        'mentee'
    );

    -- Extract first_name and last_name from metadata if provided
    user_first_name := COALESCE(
        NEW.raw_user_meta_data->>'first_name',
        NEW.raw_user_meta_data->>'firstName'
    );

    user_last_name := COALESCE(
        NEW.raw_user_meta_data->>'last_name',
        NEW.raw_user_meta_data->>'lastName'
    );

    -- Generate username with the following priority:
    -- 1. Use username from metadata if explicitly provided
    -- 2. Generate from first_name and last_name if both are available
    -- 3. Use email prefix as fallback
    IF NEW.raw_user_meta_data->>'username' IS NOT NULL THEN
        -- Explicitly provided username
        user_username := NEW.raw_user_meta_data->>'username';
    ELSIF NEW.raw_app_meta_data->>'username' IS NOT NULL THEN
        -- Username in app metadata
        user_username := NEW.raw_app_meta_data->>'username';
    ELSIF user_first_name IS NOT NULL AND user_last_name IS NOT NULL THEN
        -- Generate from first and last name: firstname_lastname_uuid_prefix
        generated_username := LOWER(CONCAT(
            REGEXP_REPLACE(user_first_name, '[^a-zA-Z0-9]', '', 'g'),
            '_',
            REGEXP_REPLACE(user_last_name, '[^a-zA-Z0-9]', '', 'g'),
            '_',
            SUBSTRING(NEW.id::TEXT FROM 1 FOR 8)
        ));
        user_username := generated_username;
    ELSE
        -- Fallback: extract part before @ from email
        user_username := SPLIT_PART(NEW.email, '@', 1);
    END IF;

    -- Insert new profile with all extracted data
    INSERT INTO public.profiles (
        id,
        email,
        username,
        first_name,
        last_name,
        role,
        is_verified,
        created_at,
        updated_at
    ) VALUES (
        NEW.id,
        NEW.email,
        user_username,
        user_first_name,
        user_last_name,
        user_role,  -- Let PostgreSQL handle type conversion automatically
        false,
        NOW(),
        NOW()
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create trigger that fires when a new user is inserted into auth.users
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

-- Add comment for documentation
COMMENT ON FUNCTION public.handle_new_user() IS 'Automatically creates a profile when a new user signs up. Generates username from metadata, names, or email prefix.';
