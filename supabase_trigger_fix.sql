-- COMPLETE Supabase Fix for Profile Creation Issues
-- Run this in your Supabase SQL Editor

-- Step 1: Check current foreign key constraints
SELECT 
    'Current Constraints:' as info,
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_name = 'profiles';

-- Step 2: Fix the foreign key constraint (THE MAIN ISSUE)
-- Drop the incorrect foreign key constraint
ALTER TABLE public.profiles 
DROP CONSTRAINT IF EXISTS profiles_id_fkey;

-- Add the correct foreign key constraint pointing to auth.users
ALTER TABLE public.profiles 
ADD CONSTRAINT profiles_id_fkey 
FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- Step 3: Fix the trigger function to handle roles properly
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
BEGIN
  INSERT INTO public.profiles (
    id, 
    email,
    role,
    is_verified,
    created_at, 
    updated_at
  )
  VALUES (
    new.id,
    new.email,
    COALESCE(
      new.raw_user_meta_data->>'role',  -- Try user_metadata first
      new.raw_app_meta_data->>'role',   -- Then app_metadata  
      'mentee'                          -- Default to mentee
    ),
    false,  -- Default not verified
    now(),
    now()
  )
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    role = COALESCE(
      new.raw_user_meta_data->>'role',
      new.raw_app_meta_data->>'role',
      profiles.role,  -- Keep existing role if no metadata
      'mentee'
    ),
    updated_at = now();
  
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Step 4: Ensure the trigger exists (recreate if needed)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- Step 5: Test the complete fix
-- This should now work without errors
DO $$
DECLARE
  test_user_id uuid;
  test_email text := 'test-fix-' || extract(epoch from now()) || '@example.com';
BEGIN
  -- Try to create a test user
  INSERT INTO auth.users (
    id,
    email,
    encrypted_password,
    email_confirmed_at,
    raw_user_meta_data,
    created_at,
    updated_at,
    confirmation_token,
    email_confirm_token
  ) VALUES (
    gen_random_uuid(),
    test_email,
    crypt('TestPassword123!', gen_salt('bf')),
    now(),
    '{"role": "mentee"}'::jsonb,
    now(),
    now(),
    '',
    ''
  ) RETURNING id INTO test_user_id;
  
  RAISE NOTICE 'SUCCESS: Test user created with ID % and email %', test_user_id, test_email;
  
  -- Check if profile was created
  IF EXISTS (SELECT 1 FROM public.profiles WHERE id = test_user_id) THEN
    RAISE NOTICE 'SUCCESS: Profile was automatically created for test user';
  ELSE
    RAISE NOTICE 'WARNING: Profile was NOT created for test user';
  END IF;
  
  -- Clean up test user
  DELETE FROM public.profiles WHERE id = test_user_id;
  DELETE FROM auth.users WHERE id = test_user_id;
  RAISE NOTICE 'Test user cleaned up';
  
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'ERROR during test: %', SQLERRM;
END $$;

-- Step 6: Verify the final setup
SELECT 
    'Final Constraint Check:' as info,
    tc.constraint_name,
    tc.table_name,
    ccu.table_name AS foreign_table_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_name = 'profiles'
    AND tc.constraint_name = 'profiles_id_fkey';

SELECT 
    'Final Trigger Check:' as info,
    trigger_name, 
    event_manipulation, 
    action_timing
FROM information_schema.triggers 
WHERE event_object_schema = 'auth' 
AND event_object_table = 'users'
AND trigger_name = 'on_auth_user_created';
