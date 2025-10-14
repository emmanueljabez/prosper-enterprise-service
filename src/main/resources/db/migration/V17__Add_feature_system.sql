-- V17__Add_feature_system.sql
-- Migration to add dynamic feature-based subscription system

-- Create features table
CREATE TABLE IF NOT EXISTS features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create plan_features junction table
CREATE TABLE IF NOT EXISTS plan_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL,
    feature_id UUID NOT NULL,
    limit_value INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_plan_features_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_features_feature FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE,
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_id)
);

-- Create subscription_addons table
CREATE TABLE IF NOT EXISTS subscription_addons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL,
    addon_type VARCHAR(50) NOT NULL,
    addon_name VARCHAR(100),
    quantity INTEGER NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    total_cost DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'KES',
    purchased_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    payment_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscription_addons_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE CASCADE,
    CONSTRAINT chk_addon_quantity CHECK (quantity > 0),
    CONSTRAINT chk_addon_used CHECK (used >= 0 AND used <= quantity)
);

-- Add new columns to subscription_plans
ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS billing_type VARCHAR(20) DEFAULT 'RECURRING',
ADD COLUMN IF NOT EXISTS yearly_cost DECIMAL(10, 2),
ADD COLUMN IF NOT EXISTS allows_addons BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS addon_session_cost DECIMAL(10, 2);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_features_code ON features(code);
CREATE INDEX IF NOT EXISTS idx_features_type ON features(type);
CREATE INDEX IF NOT EXISTS idx_plan_features_plan_id ON plan_features(plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_features_feature_id ON plan_features(feature_id);
CREATE INDEX IF NOT EXISTS idx_subscription_addons_subscription_id ON subscription_addons(subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_addons_status ON subscription_addons(status);
CREATE INDEX IF NOT EXISTS idx_subscription_addons_addon_type ON subscription_addons(addon_type);
CREATE INDEX IF NOT EXISTS idx_subscription_addons_payment_id ON subscription_addons(payment_id);

-- Add comment to features column to mark it as deprecated
COMMENT ON COLUMN subscription_plans.features IS 'DEPRECATED: Use plan_features table instead for structured feature management';
