-- Migration: Ensure payments table has payer_id column populated
-- Adds the payer_id column when missing and backfills existing records

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS payer_id UUID;

UPDATE payments
SET payer_id = user_id
WHERE payer_id IS NULL;

ALTER TABLE payments
    ALTER COLUMN payer_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_payer_id
    ON payments(payer_id);

COMMENT ON COLUMN payments.payer_id IS 'Profile ID of the payer (initiating user)';

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS recipient_id UUID;

UPDATE payments
SET recipient_id = user_id
WHERE recipient_id IS NULL;

ALTER TABLE payments
    ALTER COLUMN recipient_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_recipient_id
    ON payments(recipient_id);

COMMENT ON COLUMN payments.recipient_id IS 'Profile ID of the payment recipient';

-- Normalize status casing to match Supabase enum/check constraint
UPDATE payments
SET status = LOWER(status);

ALTER TABLE payments
    ALTER COLUMN status SET DEFAULT 'pending';
