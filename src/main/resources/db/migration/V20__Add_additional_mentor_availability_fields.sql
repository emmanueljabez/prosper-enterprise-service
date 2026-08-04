-- Add additional fields to mentor_availability table
-- These fields support one-time availability slots and meeting platform preferences

-- First, drop the NOT NULL constraint from date if it exists
ALTER TABLE mentor_availability
ALTER COLUMN date DROP NOT NULL;

-- Make date column nullable (for recurring slots)
ALTER TABLE mentor_availability
ADD COLUMN IF NOT EXISTS date DATE;

-- Add is_recurring flag to distinguish between weekly recurring and one-time slots
ALTER TABLE mentor_availability
ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN DEFAULT true;

-- Update existing rows to have is_recurring = true
UPDATE mentor_availability
SET is_recurring = true
WHERE is_recurring IS NULL;

-- Now make is_recurring NOT NULL
ALTER TABLE mentor_availability
ALTER COLUMN is_recurring SET NOT NULL;

-- Add meeting_platform to specify which platform to use for the session
ALTER TABLE mentor_availability
ADD COLUMN IF NOT EXISTS meeting_platform VARCHAR(50) DEFAULT 'zoom';

-- Add comments to explain the new columns
COMMENT ON COLUMN mentor_availability.date IS 'Specific date for one-time availability. NULL for recurring weekly slots.';
COMMENT ON COLUMN mentor_availability.is_recurring IS 'TRUE for weekly recurring slots, FALSE for one-time specific date slots';
COMMENT ON COLUMN mentor_availability.meeting_platform IS 'Preferred meeting platform (e.g., zoom, google_meet, teams)';

-- Add a check constraint to ensure logical consistency
-- Either it's recurring (date can be null) OR it has a specific date
ALTER TABLE mentor_availability
DROP CONSTRAINT IF EXISTS check_date_or_recurring;

ALTER TABLE mentor_availability
ADD CONSTRAINT check_date_or_recurring
CHECK (is_recurring = true OR date IS NOT NULL);
