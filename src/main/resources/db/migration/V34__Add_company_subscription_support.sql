-- Migration: Add corporate/company-sponsored subscription support
-- Purpose: Support corporate plans, company-owned subscriptions, seat assignments,
--          and first-class company-linked invoice/payment reporting.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        WHERE t.typname = 'user_role'
          AND e.enumlabel = 'company_admin'
    ) THEN
        ALTER TYPE user_role ADD VALUE 'company_admin';
    END IF;
END $$;

COMMENT ON TYPE user_role IS 'User roles in the system: mentee, mentor, advisee, advisor, admin, company, company_admin';

ALTER TABLE subscription_plans
ADD COLUMN IF NOT EXISTS plan_audience VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
ADD COLUMN IF NOT EXISTS min_seats INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS default_seats INTEGER NOT NULL DEFAULT 1,
ADD COLUMN IF NOT EXISTS max_seats INTEGER;

COMMENT ON COLUMN subscription_plans.plan_audience IS 'Allowed buyer audience: INDIVIDUAL, CORPORATE, BOTH';
COMMENT ON COLUMN subscription_plans.min_seats IS 'Minimum seats allowed for corporate purchases';
COMMENT ON COLUMN subscription_plans.default_seats IS 'Default seat count suggested for corporate purchases';
COMMENT ON COLUMN subscription_plans.max_seats IS 'Maximum seats allowed for corporate purchases; NULL means no hard cap';

ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS company_id UUID;

ALTER TABLE payments
ADD COLUMN IF NOT EXISTS company_id UUID;

CREATE INDEX IF NOT EXISTS idx_invoices_company_id ON invoices(company_id);
CREATE INDEX IF NOT EXISTS idx_payments_company_id ON payments(company_id);

COMMENT ON COLUMN invoices.company_id IS 'Owning company for corporate invoices when applicable';
COMMENT ON COLUMN payments.company_id IS 'Owning company for corporate payments when applicable';

CREATE TABLE IF NOT EXISTS company_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES subscription_plans(id) ON DELETE RESTRICT,
    seats_purchased INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    auto_renew BOOLEAN NOT NULL DEFAULT false,
    created_by_user_id UUID,
    latest_invoice_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_company_subscriptions_company_id
    ON company_subscriptions(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_company_subscriptions_status
    ON company_subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_company_subscriptions_period_lookup
    ON company_subscriptions(company_id, status, current_period_start, current_period_end);

COMMENT ON TABLE company_subscriptions IS 'Corporate/company-owned subscriptions purchased on behalf of employees or mentees';
COMMENT ON COLUMN company_subscriptions.seats_purchased IS 'Number of seats purchased for the current corporate subscription';
COMMENT ON COLUMN company_subscriptions.status IS 'Corporate subscription status: PENDING_PAYMENT, ACTIVE, EXPIRED, CANCELLED, SUSPENDED';
COMMENT ON COLUMN company_subscriptions.latest_invoice_id IS 'Most recent invoice linked to purchase or renewal';

CREATE TABLE IF NOT EXISTS company_subscription_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_subscription_id UUID NOT NULL REFERENCES company_subscriptions(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sessions_used INTEGER NOT NULL DEFAULT 0,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    assigned_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_company_subscription_members_unique
    ON company_subscription_members(company_subscription_id, profile_id);
CREATE INDEX IF NOT EXISTS idx_company_subscription_members_profile_lookup
    ON company_subscription_members(profile_id, status, assigned_at DESC);
CREATE INDEX IF NOT EXISTS idx_company_subscription_members_company_subscription
    ON company_subscription_members(company_subscription_id, status);

COMMENT ON TABLE company_subscription_members IS 'Seat assignments for corporate subscriptions';
COMMENT ON COLUMN company_subscription_members.status IS 'Seat assignment status: ACTIVE or REVOKED';
COMMENT ON COLUMN company_subscription_members.sessions_used IS 'Number of sessions consumed by the assigned member in the current billing period';

CREATE OR REPLACE FUNCTION update_company_subscriptions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_company_subscription_members_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_company_subscriptions_updated_at ON company_subscriptions;
CREATE TRIGGER trigger_company_subscriptions_updated_at
    BEFORE UPDATE ON company_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_company_subscriptions_updated_at();

DROP TRIGGER IF EXISTS trigger_company_subscription_members_updated_at ON company_subscription_members;
CREATE TRIGGER trigger_company_subscription_members_updated_at
    BEFORE UPDATE ON company_subscription_members
    FOR EACH ROW
    EXECUTE FUNCTION update_company_subscription_members_updated_at();
