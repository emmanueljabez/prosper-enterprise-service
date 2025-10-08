-- Migration: Update subscriptions table to use plan_id foreign key
-- Description: Replace plan_type enum with plan_id foreign key to subscription_plans

-- Add plan_id column
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_id UUID;

-- Migrate existing plan_type data to plan_id
-- Map old enum values to new plan codes
UPDATE subscriptions
SET plan_id = (
    SELECT id FROM subscription_plans WHERE code = subscriptions.plan_type
)
WHERE plan_id IS NULL AND plan_type IS NOT NULL;

-- Make plan_id NOT NULL after data migration
ALTER TABLE subscriptions ALTER COLUMN plan_id SET NOT NULL;

-- Add foreign key constraint
ALTER TABLE subscriptions
ADD CONSTRAINT fk_subscriptions_plan_id
FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
ON DELETE RESTRICT;

-- Create index on plan_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan_id ON subscriptions(plan_id);

-- Drop old plan_type column (comment out if you want to keep it for now)
-- ALTER TABLE subscriptions DROP COLUMN IF EXISTS plan_type;

-- Add comment
COMMENT ON COLUMN subscriptions.plan_id IS 'Foreign key to subscription_plans table';
