-- Migration: Add UPGRADE payment type
-- Description: Updates payment_type constraint to include UPGRADE for subscription upgrades with proration

-- Drop existing constraint if it exists
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_type_check;

-- Add updated constraint with UPGRADE type
ALTER TABLE payments ADD CONSTRAINT payments_payment_type_check
    CHECK (payment_type IN ('SESSION_BOOKING', 'SUBSCRIPTION', 'UPGRADE', 'ADDON', 'TOP_UP', 'REFUND'));

-- Update comment to reflect new payment type
COMMENT ON COLUMN payments.payment_type IS 'Payment type: SESSION_BOOKING, SUBSCRIPTION, UPGRADE, ADDON, TOP_UP, REFUND';
