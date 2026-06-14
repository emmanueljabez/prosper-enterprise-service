CREATE TABLE IF NOT EXISTS company_session_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_subscription_id UUID NOT NULL REFERENCES company_subscriptions(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    price_per_session_snapshot NUMERIC(19, 2) NOT NULL,
    sessions_purchased_total INTEGER NOT NULL DEFAULT 0,
    sessions_allocated_total INTEGER NOT NULL DEFAULT 0,
    sessions_returned_total INTEGER NOT NULL DEFAULT 0,
    sessions_available INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_session_wallet_subscription UNIQUE (company_subscription_id),
    CONSTRAINT chk_company_session_wallets_non_negative CHECK (
        sessions_purchased_total >= 0
        AND sessions_allocated_total >= 0
        AND sessions_returned_total >= 0
        AND sessions_available >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_company_session_wallets_company
    ON company_session_wallets(company_id);

CREATE TABLE IF NOT EXISTS company_session_wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES company_session_wallets(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    transaction_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type VARCHAR(60),
    reference_id VARCHAR(100),
    notes VARCHAR(255),
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_session_wallet_transaction_type CHECK (
        transaction_type IN ('PURCHASE', 'ALLOCATION_OUT', 'ALLOCATION_RETURN', 'MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT chk_company_session_wallet_transactions_non_negative CHECK (
        quantity >= 0
        AND balance_after >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_company_session_wallet_transactions_wallet_created
    ON company_session_wallet_transactions(wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_company_session_wallet_transactions_company_created
    ON company_session_wallet_transactions(company_id, created_at DESC);
