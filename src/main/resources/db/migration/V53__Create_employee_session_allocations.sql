CREATE TABLE IF NOT EXISTS employee_session_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    allocated_total INTEGER NOT NULL DEFAULT 0,
    consumed_total INTEGER NOT NULL DEFAULT 0,
    available_balance INTEGER NOT NULL DEFAULT 0,
    last_allocated_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_employee_session_allocations_company_profile UNIQUE (company_id, profile_id),
    CONSTRAINT chk_employee_session_allocations_non_negative CHECK (
        allocated_total >= 0
        AND consumed_total >= 0
        AND available_balance >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_employee_session_allocations_company_balance
    ON employee_session_allocations(company_id, available_balance DESC);

CREATE INDEX IF NOT EXISTS idx_employee_session_allocations_profile
    ON employee_session_allocations(profile_id);

CREATE TABLE IF NOT EXISTS employee_session_allocation_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_session_allocation_id UUID NOT NULL REFERENCES employee_session_allocations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    transaction_type VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type VARCHAR(60),
    reference_id VARCHAR(100),
    notes VARCHAR(255),
    created_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_employee_session_allocation_transaction_type CHECK (
        transaction_type IN (
            'ALLOCATED',
            'WITHDRAWN',
            'REALLOCATED_IN',
            'REALLOCATED_OUT',
            'BOOKED',
            'BOOKING_CANCELLED_RETURN',
            'MANUAL_ADJUSTMENT'
        )
    ),
    CONSTRAINT chk_employee_session_allocation_transactions_non_negative CHECK (
        quantity >= 0
        AND balance_after >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_employee_session_allocation_transactions_allocation_created
    ON employee_session_allocation_transactions(employee_session_allocation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_employee_session_allocation_transactions_profile_created
    ON employee_session_allocation_transactions(profile_id, created_at DESC);
