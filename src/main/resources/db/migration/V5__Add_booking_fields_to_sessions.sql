-- Add booking workflow fields to sessions table
-- This migration enhances the sessions table to handle the complete booking workflow

-- Add skill/topic reference
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS skill_id UUID;

-- Add meeting platform and details
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS meeting_platform VARCHAR(20);
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS meeting_id VARCHAR(100);
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS meeting_password VARCHAR(50);

-- Add currency and payment status
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD';
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) DEFAULT 'PENDING';

-- Add booking workflow fields
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mentee_message TEXT;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mentor_response TEXT;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS calendar_event_id VARCHAR(100);

-- Add workflow timestamps
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500);
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(10);

-- Add notification tracking
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mentee_notification_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS mentor_notification_sent BOOLEAN DEFAULT FALSE;

-- Update status column to handle enum values
-- First, update existing values to match new enum
UPDATE sessions SET status = 'SCHEDULED' WHERE status = 'scheduled';
UPDATE sessions SET status = 'COMPLETED' WHERE status = 'completed';
UPDATE sessions SET status = 'CANCELLED' WHERE status = 'cancelled';

-- Add constraints for new enum values (only if they don't exist)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sessions_status') THEN
        ALTER TABLE sessions ADD CONSTRAINT chk_sessions_status
            CHECK (status IN ('PENDING', 'CONFIRMED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sessions_meeting_platform') THEN
        ALTER TABLE sessions ADD CONSTRAINT chk_sessions_meeting_platform
            CHECK (meeting_platform IN ('GOOGLE_MEET', 'ZOOM'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sessions_payment_status') THEN
        ALTER TABLE sessions ADD CONSTRAINT chk_sessions_payment_status
            CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'FAILED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_sessions_cancelled_by') THEN
        ALTER TABLE sessions ADD CONSTRAINT chk_sessions_cancelled_by
            CHECK (cancelled_by IN ('MENTEE', 'MENTOR', 'SYSTEM', 'ADMIN'));
    END IF;
END $$;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_sessions_skill_id ON sessions(skill_id);
CREATE INDEX IF NOT EXISTS idx_sessions_meeting_platform ON sessions(meeting_platform);
CREATE INDEX IF NOT EXISTS idx_sessions_payment_status ON sessions(payment_status);
CREATE INDEX IF NOT EXISTS idx_sessions_mentor_status ON sessions(mentor_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_mentee_status ON sessions(mentee_id, status);

-- Composite index for conflict checking
CREATE INDEX IF NOT EXISTS idx_sessions_mentor_time_status 
    ON sessions(mentor_id, scheduled_start, scheduled_end, status);

-- Index for reminder processing
CREATE INDEX IF NOT EXISTS idx_sessions_reminder_processing 
    ON sessions(status, reminder_sent, scheduled_start) 
    WHERE status = 'CONFIRMED' AND reminder_sent = FALSE;

-- Foreign key constraint for skill reference (if referential integrity is enforced)
-- ALTER TABLE sessions ADD CONSTRAINT fk_sessions_skill FOREIGN KEY (skill_id) REFERENCES skills(id);

-- Comments for new fields
COMMENT ON COLUMN sessions.skill_id IS 'Reference to the skill/topic for this session';
COMMENT ON COLUMN sessions.meeting_platform IS 'Meeting platform: ZOOM or GOOGLE_MEET';
COMMENT ON COLUMN sessions.meeting_id IS 'Platform-specific meeting ID';
COMMENT ON COLUMN sessions.meeting_password IS 'Meeting password if required';
COMMENT ON COLUMN sessions.mentee_message IS 'Message from mentee when requesting the session';
COMMENT ON COLUMN sessions.mentor_response IS 'Mentor response when confirming the session';
COMMENT ON COLUMN sessions.calendar_event_id IS 'Google Calendar event ID';
COMMENT ON COLUMN sessions.confirmed_at IS 'Timestamp when mentor confirmed the session';
COMMENT ON COLUMN sessions.cancelled_at IS 'Timestamp when session was cancelled';
COMMENT ON COLUMN sessions.cancelled_by IS 'Who cancelled the session: MENTEE, MENTOR, SYSTEM, or ADMIN';
COMMENT ON COLUMN sessions.payment_status IS 'Payment processing status: PENDING, PAID, REFUNDED, FAILED';
