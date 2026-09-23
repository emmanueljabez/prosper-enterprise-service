CREATE TABLE company_join_links (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    token_nonce varchar(96) NOT NULL,
    token_hash varchar(128) NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    created_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    revoked_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    revoked_at timestamp,
    last_used_at timestamp,
    CONSTRAINT chk_company_join_links_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE UNIQUE INDEX uniq_company_join_links_active_company
    ON company_join_links(company_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_company_join_links_company_status
    ON company_join_links(company_id, status);

CREATE INDEX idx_company_join_links_token_hash
    ON company_join_links(token_hash);
