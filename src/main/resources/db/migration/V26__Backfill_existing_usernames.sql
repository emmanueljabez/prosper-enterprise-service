-- Migration to backfill usernames for existing profiles that don't have one
-- This ensures backward compatibility with existing data
-- Uses the existing generate_username_from_name function for consistency

-- Check if username column is nullable and update existing NULL values
DO $$
DECLARE
    null_count INTEGER;
BEGIN
    -- Count how many profiles have NULL username
    SELECT COUNT(*) INTO null_count
    FROM public.profiles
    WHERE username IS NULL;

    -- Log the count
    RAISE NOTICE 'Found % profiles with NULL username that need to be backfilled', null_count;

    -- Only proceed if there are NULL usernames
    IF null_count > 0 THEN
        -- Temporarily drop NOT NULL constraint if it exists
        BEGIN
            ALTER TABLE public.profiles ALTER COLUMN username DROP NOT NULL;
            RAISE NOTICE 'Temporarily removed NOT NULL constraint from username column';
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'Username column was already nullable';
        END;

        -- Update existing profiles that have NULL username
        -- Use the existing generate_username_from_name function for consistency with current behavior
        UPDATE public.profiles
        SET username = CASE
            -- If first_name and last_name are available, use the existing function
            WHEN first_name IS NOT NULL AND last_name IS NOT NULL THEN
                generate_username_from_name(first_name, last_name, id)
            -- If only first_name is available, use it with the existing function (pass empty string for last_name)
            WHEN first_name IS NOT NULL THEN
                generate_username_from_name(first_name, '', id)
            -- Fallback to email prefix with UUID suffix for uniqueness
            ELSE
                CONCAT(
                    SPLIT_PART(email, '@', 1),
                    '_',
                    SUBSTRING(id::TEXT FROM 1 FOR 8)
                )
        END
        WHERE username IS NULL;

        RAISE NOTICE 'Backfilled % usernames', null_count;

        -- Now make username NOT NULL again
        ALTER TABLE public.profiles ALTER COLUMN username SET NOT NULL;
        RAISE NOTICE 'Restored NOT NULL constraint on username column';
    ELSE
        RAISE NOTICE 'All profiles already have usernames, no backfill needed';
    END IF;
END $$;

-- Add a unique constraint on username if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'profiles_username_unique'
    ) THEN
        -- Before adding unique constraint, handle any duplicate usernames
        -- by appending a counter to duplicates
        WITH duplicates AS (
            SELECT username, ROW_NUMBER() OVER (PARTITION BY username ORDER BY created_at) as rn
            FROM public.profiles
            WHERE username IS NOT NULL
        )
        UPDATE public.profiles p
        SET username = p.username || '_' || d.rn
        FROM duplicates d
        WHERE p.username = d.username AND d.rn > 1;

        -- Now add the unique constraint
        ALTER TABLE public.profiles ADD CONSTRAINT profiles_username_unique UNIQUE (username);
        RAISE NOTICE 'Added unique constraint on username column';
    ELSE
        RAISE NOTICE 'Unique constraint already exists on username column';
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON COLUMN public.profiles.username IS 'Unique username for the user. Generated from email or name if not provided.';
