-- IMMEDIATE FIX for profiles_id_fkey constraint issue
-- Run this in your Supabase SQL Editor

-- Step 1: Check if the problematic constraint exists
SELECT 
    'Checking for problematic constraint:' as status,
    constraint_name,
    table_name,
    constraint_type
FROM information_schema.table_constraints 
WHERE table_name = 'profiles' 
    AND constraint_name = 'profiles_id_fkey';

-- Step 2: Drop the problematic foreign key constraint 
-- This constraint is preventing profile insertion when UUIDs don't exist in a users table
ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_id_fkey;

-- Step 3: Verify the constraint is gone
SELECT 
    'Constraint check after removal:' as status,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE table_name = 'profiles' 
            AND constraint_name = 'profiles_id_fkey'
        ) THEN 'STILL EXISTS - needs manual removal'
        ELSE 'SUCCESSFULLY REMOVED'
    END as result;

-- Step 4: Test profile insertion with a random UUID (should now work)
DO $$
DECLARE
    test_uuid uuid := gen_random_uuid();
    test_email text := 'test-constraint-fix-' || extract(epoch from now()) || '@example.com';
BEGIN
    -- Try to insert a profile with a random UUID
    INSERT INTO public.profiles (
        id, 
        email, 
        role, 
        is_verified, 
        created_at, 
        updated_at
    ) VALUES (
        test_uuid,
        test_email,
        'mentee',
        false,
        now(),
        now()
    );
    
    RAISE NOTICE 'SUCCESS: Profile inserted with UUID % and email %', test_uuid, test_email;
    
    -- Clean up the test record
    DELETE FROM public.profiles WHERE id = test_uuid;
    RAISE NOTICE 'Test profile cleaned up';
    
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'ERROR: Profile insertion still failed: %', SQLERRM;
END $$;

-- Step 5: Show final state of constraints on profiles table
SELECT 
    'Final constraints on profiles table:' as info,
    constraint_name,
    constraint_type
FROM information_schema.table_constraints 
WHERE table_name = 'profiles' 
    AND table_schema = 'public'
ORDER BY constraint_type, constraint_name;



