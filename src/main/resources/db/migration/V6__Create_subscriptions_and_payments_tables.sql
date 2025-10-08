-- Migration: Create subscriptions and payments tables
-- Description: Adds subscription management and Mpesa payment tracking

-- Create subscriptions table
CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    sessions_per_month INTEGER NOT NULL,
    sessions_used INTEGER NOT NULL DEFAULT 0,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    current_period_start TIMESTAMP NOT NULL,
    current_period_end TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    auto_renew BOOLEAN NOT NULL DEFAULT true,
    is_trial BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on user_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);

-- Create index on status for filtering
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);

-- Create index for finding active subscriptions
CREATE INDEX IF NOT EXISTS idx_subscriptions_active_lookup
    ON subscriptions(user_id, status, start_date, end_date);

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    session_id UUID NULL,
    subscription_id UUID NULL,
    payment_type VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    phone_number VARCHAR(20),
    checkout_request_id VARCHAR(100),
    merchant_request_id VARCHAR(100),
    mpesa_receipt_number VARCHAR(50),
    transaction_date TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50) NOT NULL DEFAULT 'MPESA',
    description VARCHAR(500),
    result_description VARCHAR(500),
    result_code INTEGER,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create index on user_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);

-- Create index on session_id for session payments
CREATE INDEX IF NOT EXISTS idx_payments_session_id ON payments(session_id);

-- Create index on subscription_id for subscription payments
CREATE INDEX IF NOT EXISTS idx_payments_subscription_id ON payments(subscription_id);

-- Create index on checkout_request_id for Mpesa callbacks
CREATE INDEX IF NOT EXISTS idx_payments_checkout_request_id ON payments(checkout_request_id);

-- Create index on status for filtering
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- Create index on payment_type and status for queries
CREATE INDEX IF NOT EXISTS idx_payments_type_status ON payments(payment_type, status);

-- Create index on created_at for time-based queries
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at);

-- Add foreign key constraints (optional, uncomment if you want to enforce referential integrity)
-- Note: These are commented out as they may fail if the referenced tables don't have matching columns
-- ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_user_id
--     FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE;

-- ALTER TABLE payments ADD CONSTRAINT fk_payments_user_id
--     FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE;

-- ALTER TABLE payments ADD CONSTRAINT fk_payments_session_id
--     FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL;

-- ALTER TABLE payments ADD CONSTRAINT fk_payments_subscription_id
--     FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE SET NULL;

-- Add trigger to update updated_at timestamp on subscriptions
CREATE OR REPLACE FUNCTION update_subscriptions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_subscriptions_updated_at
    BEFORE UPDATE ON subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_subscriptions_updated_at();

-- Add trigger to update updated_at timestamp on payments
CREATE OR REPLACE FUNCTION update_payments_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_payments_updated_at();

-- Add comments to tables
COMMENT ON TABLE subscriptions IS 'Tracks user subscription plans and usage';
COMMENT ON TABLE payments IS 'Tracks Mpesa and other payment transactions';

COMMENT ON COLUMN subscriptions.plan_type IS 'Subscription plan: FREE, BASIC, STANDARD, PREMIUM, UNLIMITED';
COMMENT ON COLUMN subscriptions.sessions_per_month IS 'Number of sessions allowed per month';
COMMENT ON COLUMN subscriptions.sessions_used IS 'Number of sessions consumed in current period';
COMMENT ON COLUMN subscriptions.status IS 'Subscription status: ACTIVE, EXPIRED, CANCELLED, SUSPENDED, TRIAL';

COMMENT ON COLUMN payments.payment_type IS 'Payment type: SESSION_BOOKING, SUBSCRIPTION, TOP_UP, REFUND';
COMMENT ON COLUMN payments.payment_method IS 'Payment method: MPESA, CARD, BANK_TRANSFER, PAYPAL, FREE';
COMMENT ON COLUMN payments.status IS 'Payment status: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED, REFUNDED';