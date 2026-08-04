CREATE TABLE company_mentor_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    email varchar(255) NOT NULL,
    phone varchar(32) NOT NULL,
    first_name varchar(120),
    last_name varchar(120),
    title varchar(180),
    department varchar(180),
    tags text[],
    default_visibility varchar(40) NOT NULL DEFAULT 'COMPANY_PRIVATE',
    program_or_cohort_reference varchar(255),
    invitation_token_hash varchar(128),
    invitation_token_expires_at timestamp,
    status varchar(40) NOT NULL DEFAULT 'DRAFT',
    email_delivery_status varchar(40) NOT NULL DEFAULT 'NOT_ATTEMPTED',
    whatsapp_delivery_status varchar(40) NOT NULL DEFAULT 'NOT_ATTEMPTED',
    accepted_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    accepted_at timestamp,
    invited_by_user_id uuid,
    last_sent_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_mentor_invitation_visibility CHECK (default_visibility IN ('COMPANY_PRIVATE', 'PROGRAM_RESTRICTED', 'PUBLIC_REQUESTED', 'PUBLIC_APPROVED')),
    CONSTRAINT chk_company_mentor_invitation_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'FAILED_DELIVERY')),
    CONSTRAINT chk_company_mentor_invitation_email_delivery CHECK (email_delivery_status IN ('NOT_ATTEMPTED', 'SENT', 'FAILED', 'DELIVERED')),
    CONSTRAINT chk_company_mentor_invitation_whatsapp_delivery CHECK (whatsapp_delivery_status IN ('NOT_ATTEMPTED', 'SENT', 'FAILED', 'DELIVERED'))
);

CREATE UNIQUE INDEX uniq_company_mentor_open_invitation_email
    ON company_mentor_invitations(company_id, lower(email))
    WHERE status IN ('DRAFT', 'SENT', 'FAILED_DELIVERY');

CREATE UNIQUE INDEX uniq_company_mentor_open_invitation_phone
    ON company_mentor_invitations(company_id, phone)
    WHERE status IN ('DRAFT', 'SENT', 'FAILED_DELIVERY');

CREATE INDEX idx_company_mentor_invitations_company_status
    ON company_mentor_invitations(company_id, status);

CREATE INDEX idx_company_mentor_invitations_token_hash
    ON company_mentor_invitations(invitation_token_hash)
    WHERE invitation_token_hash IS NOT NULL;

CREATE TABLE company_mentor_pool_memberships (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    mentor_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    source_invitation_id uuid REFERENCES company_mentor_invitations(id) ON DELETE SET NULL,
    visibility_mode varchar(40) NOT NULL DEFAULT 'COMPANY_PRIVATE',
    membership_status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    profile_complete boolean NOT NULL DEFAULT false,
    availability_complete boolean NOT NULL DEFAULT false,
    company_bookable boolean NOT NULL DEFAULT false,
    public_approval_status varchar(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    public_requested_at timestamp,
    public_approved_at timestamp,
    public_approved_by_user_id uuid,
    public_listing_preexisting boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_mentor_membership_visibility CHECK (visibility_mode IN ('COMPANY_PRIVATE', 'PROGRAM_RESTRICTED', 'PUBLIC_REQUESTED', 'PUBLIC_APPROVED')),
    CONSTRAINT chk_company_mentor_membership_status CHECK (membership_status IN ('PENDING_INVITE', 'ACTIVE', 'REMOVED', 'SUSPENDED')),
    CONSTRAINT chk_company_mentor_public_approval CHECK (public_approval_status IN ('NOT_REQUESTED', 'REQUESTED', 'APPROVED', 'REJECTED'))
);

CREATE UNIQUE INDEX uniq_company_mentor_active_membership
    ON company_mentor_pool_memberships(company_id, mentor_profile_id)
    WHERE membership_status IN ('PENDING_INVITE', 'ACTIVE', 'SUSPENDED');

CREATE INDEX idx_company_mentor_memberships_company
    ON company_mentor_pool_memberships(company_id, membership_status);

CREATE INDEX idx_company_mentor_memberships_mentor
    ON company_mentor_pool_memberships(mentor_profile_id);

CREATE INDEX idx_company_mentor_memberships_visibility
    ON company_mentor_pool_memberships(visibility_mode, public_approval_status, company_bookable);

CREATE TABLE company_mentor_program_scopes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_mentor_pool_membership_id uuid NOT NULL REFERENCES company_mentor_pool_memberships(id) ON DELETE CASCADE,
    company_program_id uuid REFERENCES company_programs(id) ON DELETE CASCADE,
    cohort_id uuid,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_mentor_scope_target CHECK (company_program_id IS NOT NULL OR cohort_id IS NOT NULL)
);

CREATE UNIQUE INDEX uniq_company_mentor_program_scope
    ON company_mentor_program_scopes(company_mentor_pool_membership_id, company_program_id)
    WHERE company_program_id IS NOT NULL;

CREATE UNIQUE INDEX uniq_company_mentor_cohort_scope
    ON company_mentor_program_scopes(company_mentor_pool_membership_id, cohort_id)
    WHERE cohort_id IS NOT NULL;

CREATE INDEX idx_company_mentor_scopes_program
    ON company_mentor_program_scopes(company_program_id);
