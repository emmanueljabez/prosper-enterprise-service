CREATE TABLE IF NOT EXISTS b2b_demo_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(160) NOT NULL,
    work_email VARCHAR(254) NOT NULL,
    organisation VARCHAR(200) NOT NULL,
    phone_number VARCHAR(60),
    partnership_type VARCHAR(80),
    cohort_size VARCHAR(80),
    timeline VARCHAR(120),
    details TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    source_page VARCHAR(120) NOT NULL DEFAULT 'enterprise-pricing',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_b2b_demo_requests_status CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_b2b_demo_requests_created_at
    ON b2b_demo_requests(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_b2b_demo_requests_status_created_at
    ON b2b_demo_requests(status, created_at DESC);
