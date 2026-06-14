ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS entitlement_source VARCHAR(40),
    ADD COLUMN IF NOT EXISTS consumed_subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS consumed_subscription_addon_id UUID REFERENCES subscription_addons(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS entitlement_returned_at TIMESTAMP;

ALTER TABLE sessions
    ADD CONSTRAINT chk_sessions_entitlement_source
    CHECK (
        entitlement_source IS NULL
        OR entitlement_source IN (
            'CORPORATE_ALLOCATION',
            'PERSONAL_CREDIT',
            'INDIVIDUAL_SUBSCRIPTION',
            'SUBSCRIPTION_ADDON'
        )
    )
    NOT VALID;

CREATE INDEX IF NOT EXISTS idx_sessions_consumed_subscription
    ON sessions(consumed_subscription_id);

CREATE INDEX IF NOT EXISTS idx_sessions_consumed_subscription_addon
    ON sessions(consumed_subscription_addon_id);
