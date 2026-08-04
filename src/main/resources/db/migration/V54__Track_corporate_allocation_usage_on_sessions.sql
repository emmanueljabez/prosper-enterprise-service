ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS corporate_allocation_id UUID REFERENCES employee_session_allocations(id) ON DELETE SET NULL;

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS corporate_allocation_consumed_at TIMESTAMP;

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS corporate_allocation_returned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_sessions_corporate_allocation
    ON sessions(corporate_allocation_id);
