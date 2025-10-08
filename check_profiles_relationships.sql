-- Comprehensive SQL Query to Check All Relationships for the 'profiles' Table
-- Run this in your Supabase SQL Editor

-- 1. Check all foreign key constraints FROM profiles table (what profiles references)
SELECT 
    'OUTGOING Foreign Keys (profiles -> other tables):' as relationship_type,
    tc.constraint_name,
    tc.table_name as source_table,
    kcu.column_name as source_column,
    ccu.table_schema as target_schema,
    ccu.table_name as target_table,
    ccu.column_name as target_column,
    rc.delete_rule,
    rc.update_rule
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
JOIN information_schema.referential_constraints AS rc
    ON tc.constraint_name = rc.constraint_name
    AND tc.table_schema = rc.constraint_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_name = 'profiles'
ORDER BY tc.constraint_name;

-- 2. Check all foreign key constraints TO profiles table (what references profiles)
SELECT 
    'INCOMING Foreign Keys (other tables -> profiles):' as relationship_type,
    tc.constraint_name,
    tc.table_schema as source_schema,
    tc.table_name as source_table,
    kcu.column_name as source_column,
    ccu.table_name as target_table,
    ccu.column_name as target_column,
    rc.delete_rule,
    rc.update_rule
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
JOIN information_schema.referential_constraints AS rc
    ON tc.constraint_name = rc.constraint_name
    AND tc.table_schema = rc.constraint_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND ccu.table_name = 'profiles'
ORDER BY tc.constraint_name;

-- 3. Check all tables in the database to see which ones exist
SELECT 
    'All Tables in Database:' as info,
    table_schema,
    table_name,
    table_type
FROM information_schema.tables 
WHERE table_schema IN ('public', 'auth')
    AND table_type = 'BASE TABLE'
ORDER BY table_schema, table_name;

-- 4. Check the specific constraint that's causing the issue
SELECT 
    'Problematic Constraint Details:' as info,
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_schema as referenced_schema,
    ccu.table_name as referenced_table,
    ccu.column_name as referenced_column
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.constraint_name = 'profiles_id_fkey';

-- 5. Check if 'users' table exists in public schema
SELECT 
    'Does public.users table exist?' as question,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = 'public' 
            AND table_name = 'users'
        ) THEN 'YES - public.users exists'
        ELSE 'NO - public.users does not exist'
    END as answer;

-- 6. Check if 'auth.users' table exists
SELECT 
    'Does auth.users table exist?' as question,
    CASE 
        WHEN EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = 'auth' 
            AND table_name = 'users'
        ) THEN 'YES - auth.users exists'
        ELSE 'NO - auth.users does not exist'
    END as answer;

-- 7. Show the structure of the profiles table
SELECT 
    'Profiles Table Structure:' as info,
    column_name,
    data_type,
    is_nullable,
    column_default,
    character_maximum_length
FROM information_schema.columns 
WHERE table_schema = 'public' 
    AND table_name = 'profiles'
ORDER BY ordinal_position;

-- 8. Check for any indexes on profiles table
SELECT 
    'Indexes on profiles table:' as info,
    indexname,
    indexdef
FROM pg_indexes 
WHERE tablename = 'profiles' 
    AND schemaname = 'public';

-- 9. Show sample data from auth.users if it exists (first 3 rows)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'auth' AND table_name = 'users'
    ) THEN
        RAISE NOTICE 'Sample auth.users data:';
        FOR rec IN 
            SELECT id, email, created_at 
            FROM auth.users 
            LIMIT 3
        LOOP
            RAISE NOTICE 'ID: %, Email: %, Created: %', rec.id, rec.email, rec.created_at;
        END LOOP;
    ELSE
        RAISE NOTICE 'auth.users table does not exist';
    END IF;
END $$;

-- 10. Show sample data from public.users if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN
        RAISE NOTICE 'Sample public.users data:';
        FOR rec IN 
            SELECT id, email, created_at 
            FROM public.users 
            LIMIT 3
        LOOP
            RAISE NOTICE 'ID: %, Email: %, Created: %', rec.id, rec.email, rec.created_at;
        END LOOP;
    ELSE
        RAISE NOTICE 'public.users table does not exist';
    END IF;
EXCEPTION 
    WHEN undefined_table THEN
        RAISE NOTICE 'public.users table does not exist (caught exception)';
    WHEN OTHERS THEN
        RAISE NOTICE 'Error accessing public.users: %', SQLERRM;
END $$;



