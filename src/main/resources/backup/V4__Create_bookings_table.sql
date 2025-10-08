-- Create bookings table for session booking management
-- This follows the complete booking workflow from request to completion

CREATE TABLE IF NOT EXISTS bookings (
    -- Primary key and identifiers
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Foreign keys
    mentor_id UUID NOT NULL,
    mentee_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    
    -- Session timing
    requested_start_time TIMESTAMPTZ NOT NULL,
    requested_end_time TIMESTAMPTZ NOT NULL,
    
    -- Meeting configuration
    meeting_platform VARCHAR(20) NOT NULL CHECK (meeting_platform IN ('ZOOM', 'GOOGLE_MEET')),
    mentee_message TEXT,
    
    -- Booking status and workflow
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    
    -- Meeting details (populated after confirmation)
    meeting_url VARCHAR(500),
    meeting_id VARCHAR(100),
    meeting_password VARCHAR(50),
    calendar_event_id VARCHAR(100),
    
    -- Financial information
    price DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'USD',
    payment_status VARCHAR(20) DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'FAILED')),
    
    -- Communication and responses
    mentor_response TEXT,
    
    -- Workflow timestamps
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(500),
    cancelled_by VARCHAR(10) CHECK (cancelled_by IN ('MENTEE', 'MENTOR', 'SYSTEM', 'ADMIN')),
    
    -- Notification tracking
    mentee_notification_sent BOOLEAN DEFAULT FALSE,
    mentor_notification_sent BOOLEAN DEFAULT FALSE,
    reminder_sent BOOLEAN DEFAULT FALSE,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_bookings_mentor_id ON bookings(mentor_id);
CREATE INDEX idx_bookings_mentee_id ON bookings(mentee_id);
CREATE INDEX idx_bookings_skill_id ON bookings(skill_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_requested_start_time ON bookings(requested_start_time);
CREATE INDEX idx_bookings_mentor_status ON bookings(mentor_id, status);
CREATE INDEX idx_bookings_mentee_status ON bookings(mentee_id, status);

-- Composite index for conflict checking
CREATE INDEX idx_bookings_mentor_time_status ON bookings(mentor_id, requested_start_time, requested_end_time, status);

-- Index for reminder processing
CREATE INDEX idx_bookings_reminder_processing ON bookings(status, reminder_sent, requested_start_time) 
WHERE status = 'CONFIRMED' AND reminder_sent = FALSE;

-- Foreign key constraints (if referential integrity is enforced)
-- Note: These may need to be adjusted based on your specific table structure
-- ALTER TABLE bookings ADD CONSTRAINT fk_bookings_mentor FOREIGN KEY (mentor_id) REFERENCES mentor_profiles(id);
-- ALTER TABLE bookings ADD CONSTRAINT fk_bookings_mentee FOREIGN KEY (mentee_id) REFERENCES mentee_profiles(id);
-- ALTER TABLE bookings ADD CONSTRAINT fk_bookings_skill FOREIGN KEY (skill_id) REFERENCES skills(id);

-- Add booking_id column to sessions table to link sessions with bookings
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS booking_id UUID;
CREATE INDEX IF NOT EXISTS idx_sessions_booking_id ON sessions(booking_id);

-- Update trigger for updated_at timestamp
CREATE OR REPLACE FUNCTION update_bookings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_bookings_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW
    EXECUTE FUNCTION update_bookings_updated_at();

-- Comments for documentation
COMMENT ON TABLE bookings IS 'Stores mentorship session booking requests and their complete workflow';
COMMENT ON COLUMN bookings.status IS 'Booking workflow status: PENDING (awaiting mentor confirmation), CONFIRMED (meeting scheduled), CANCELLED, COMPLETED, NO_SHOW';
COMMENT ON COLUMN bookings.meeting_platform IS 'Preferred meeting platform: ZOOM or GOOGLE_MEET';
COMMENT ON COLUMN bookings.payment_status IS 'Payment processing status for the session';
COMMENT ON COLUMN bookings.cancelled_by IS 'Who initiated the cancellation: MENTEE, MENTOR, SYSTEM, or ADMIN';


