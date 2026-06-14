-- Ensure profile usernames remain unique even when auth users are created concurrently
-- or by paths outside the application-level username generator.

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
DECLARE
    user_role TEXT;
    user_username TEXT;
    user_first_name TEXT;
    user_last_name TEXT;
    generated_username TEXT;
    username_suffix TEXT;
BEGIN
    user_role := COALESCE(
        NEW.raw_user_meta_data->>'role',
        NEW.raw_app_meta_data->>'role',
        'mentee'
    );

    user_first_name := COALESCE(
        NEW.raw_user_meta_data->>'first_name',
        NEW.raw_user_meta_data->>'firstName'
    );

    user_last_name := COALESCE(
        NEW.raw_user_meta_data->>'last_name',
        NEW.raw_user_meta_data->>'lastName'
    );

    IF NEW.raw_user_meta_data->>'username' IS NOT NULL THEN
        user_username := NEW.raw_user_meta_data->>'username';
    ELSIF NEW.raw_app_meta_data->>'username' IS NOT NULL THEN
        user_username := NEW.raw_app_meta_data->>'username';
    ELSIF user_first_name IS NOT NULL AND user_last_name IS NOT NULL THEN
        generated_username := LOWER(CONCAT(
            REGEXP_REPLACE(user_first_name, '[^a-zA-Z0-9]', '', 'g'),
            '_',
            REGEXP_REPLACE(user_last_name, '[^a-zA-Z0-9]', '', 'g')
        ));
        user_username := generated_username;
    ELSE
        user_username := SPLIT_PART(NEW.email, '@', 1);
    END IF;

    user_username := LOWER(COALESCE(user_username, ''));
    user_username := REGEXP_REPLACE(user_username, '[^a-z0-9_]+', '_', 'g');
    user_username := REGEXP_REPLACE(user_username, '_+', '_', 'g');
    user_username := REGEXP_REPLACE(user_username, '^_+|_+$', '', 'g');

    IF user_username IS NULL OR user_username = '' THEN
        user_username := 'user';
    END IF;

    IF EXISTS (SELECT 1 FROM public.profiles WHERE username = user_username) THEN
        username_suffix := SUBSTRING(NEW.id::TEXT FROM 1 FOR 8);
        user_username := CONCAT(user_username, '_', username_suffix);
    END IF;

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
        user_role,
        false,
        NOW(),
        NOW()
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_user();

COMMENT ON FUNCTION public.handle_new_user() IS 'Automatically creates a profile when a new user signs up. Normalizes usernames and appends a UUID suffix if the preferred username is already taken.';
