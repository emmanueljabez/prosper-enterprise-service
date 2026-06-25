ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS booking_source VARCHAR(30) NOT NULL DEFAULT 'ENTERPRISE';

UPDATE sessions
SET booking_source = 'ENTERPRISE'
WHERE booking_source IS NULL;

