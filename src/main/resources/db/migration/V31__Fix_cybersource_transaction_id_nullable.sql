-- Migration: Fix CyberSource transaction_id to be nullable
-- Purpose: transaction_id is returned by CyberSource after payment, not during initiation
-- Issue: V30 incorrectly set it as NOT NULL, causing constraint violations

-- Make transaction_id nullable since it's only populated after CyberSource callback
ALTER TABLE cybersource_transactions
ALTER COLUMN transaction_id DROP NOT NULL;

-- Add comment explaining why it's nullable
COMMENT ON COLUMN cybersource_transactions.transaction_id IS 'Transaction ID from CyberSource (nullable until callback received)';
