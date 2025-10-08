-- Fix Flyway checksum issue for V5 migration
-- Run this in your Supabase SQL Editor

-- Option 1: Mark V5 as successful and repair checksum
-- This deletes the failed V5 record and lets it run again with new checksum
DELETE FROM flyway_schema_history WHERE version = '5' AND success = false;

-- If V5 shows as successful but with wrong checksum, update it:
-- UPDATE flyway_schema_history SET checksum = NULL WHERE version = '5';

-- Verify the fix
SELECT version, description, type, script, checksum, installed_on, execution_time, success
FROM flyway_schema_history
ORDER BY installed_rank;