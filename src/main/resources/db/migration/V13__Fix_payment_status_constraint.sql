-- Migration: Fix payment status constraint to accept lowercase values
-- Description: Updates the payments_status_check constraint to match the PaymentStatusConverter
--              which converts enum values to lowercase

-- Drop existing check constraint if it exists
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;

-- Add new check constraint with lowercase values
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('pending', 'completed', 'failed', 'cancelled', 'refunded'));

-- Update comment to reflect lowercase values
COMMENT ON COLUMN payments.status IS 'Payment status: pending, completed, failed, cancelled, refunded (lowercase to match JPA converter)';
