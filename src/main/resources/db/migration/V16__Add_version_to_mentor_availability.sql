-- Add version column for optimistic locking
ALTER TABLE mentor_availability
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Set existing rows to version 0
UPDATE mentor_availability
SET version = 0
WHERE version IS NULL;

-- Add comment
COMMENT ON COLUMN mentor_availability.version IS 'Version column for optimistic locking';
