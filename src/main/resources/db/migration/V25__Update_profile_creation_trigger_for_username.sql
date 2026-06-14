-- Migration to update the handle_new_user trigger to extract username from user metadata
-- This ensures that when a new user is created via Supabase Auth, the username field is populated
-- Maintains backward compatibility with existing generate_username_from_name trigger

-- Drop existing trigger and function if they exist
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

    -- Extract or generate username with the following priority:
    -- 1. Use username from metadata if provided
    -- 2. Use email prefix as fallback
    -- Note: If first_name and last_name are provided but no username,
    -- we set username to NULL so the existing generate_username_from_name trigger can handle it
    user_username := COALESCE(
        NEW.raw_user_meta_data->>'username',
        NEW.raw_app_meta_data->>'username',
        -- If we have first and last name but no username, set to NULL
        -- This allows the existing generate_username_from_name trigger to work
        CASE
            WHEN user_first_name IS NOT NULL AND user_last_name IS NOT NULL THEN NULL
            ELSE SPLIT_PART(NEW.email, '@', 1)  -- Fallback: extract part before @ from email
        END
    );

    -- Insert new profile with email, role, username (possibly NULL), and optional first/last name
    -- If username is NULL and first_name/last_name exist, the generate_username_from_name trigger will fill it
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
        user_username,  -- Can be NULL to allow generate_username_from_name trigger to work
        user_first_name,
        user_last_name,
        user_role::user_role,  -- Cast to the user_role enum type
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
COMMENT ON FUNCTION public.handle_new_user() IS 'Automatically creates a profile when a new user signs up. Extracts username, first_name, and last_name from metadata. Falls back to existing generate_username_from_name trigger for username generation when first/last names are provided.';
