-- Migration: Add PENDING_PAYMENT status to subscriptions
-- Description: Adds new status for subscriptions awaiting payment confirmation

-- Note: PostgreSQL doesn't require explicit ALTER TYPE for enums if the application handles it
-- The PENDING_PAYMENT status will be added by the application layer
-- This migration is for documentation and future reference

-- Add comment to status column
COMMENT ON COLUMN subscriptions.status IS 'Subscription status: PENDING_PAYMENT, ACTIVE, EXPIRED, CANCELLED, SUSPENDED, TRIAL';

-- Add index for pending payment cleanup queries
CREATE INDEX IF NOT EXISTS idx_subscriptions_status_created_at
    ON subscriptions(status, created_at)
    WHERE status = 'PENDING_PAYMENT';

-- Add comment
COMMENT ON INDEX idx_subscriptions_status_created_at IS 'Index for cleanup of expired pending payment subscriptions';
