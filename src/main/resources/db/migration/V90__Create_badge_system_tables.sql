CREATE TABLE IF NOT EXISTS badge_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description TEXT,
    category VARCHAR(40) NOT NULL,
    award_method VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    label VARCHAR(80) NOT NULL,
    color_hex VARCHAR(7) NOT NULL DEFAULT '#8f1f74',
    background_hex VARCHAR(7) NOT NULL DEFAULT '#f7e8f3',
    text_hex VARCHAR(7) NOT NULL DEFAULT '#6f1859',
    display_order INTEGER NOT NULL DEFAULT 100,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at TIMESTAMP,
    CONSTRAINT ck_badge_types_category CHECK (category IN ('AFFILIATION', 'ROLE_STATUS', 'RECOGNITION')),
    CONSTRAINT ck_badge_types_award_method CHECK (award_method IN ('AFFILIATION_RULE', 'MANUAL', 'METRIC')),
    CONSTRAINT ck_badge_types_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_badge_types_color_hex CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_badge_types_background_hex CHECK (background_hex ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_badge_types_text_hex CHECK (text_hex ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_badge_types_slug
    ON badge_types (slug);

CREATE INDEX IF NOT EXISTS idx_badge_types_status_category
    ON badge_types (status, category, display_order);

CREATE TABLE IF NOT EXISTS profile_badge_awards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    badge_type_id UUID NOT NULL REFERENCES badge_types(id),
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_type VARCHAR(40) NOT NULL,
    source_ref_id UUID,
    source_label VARCHAR(180),
    award_note TEXT,
    revoke_note TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    primary_selected_by_user BOOLEAN NOT NULL DEFAULT false,
    visibility VARCHAR(20) NOT NULL DEFAULT 'SHOWN',
    awarded_by UUID,
    awarded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by UUID,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_profile_badge_awards_status CHECK (status IN ('ACTIVE', 'REVOKED', 'WITHDRAWN')),
    CONSTRAINT ck_profile_badge_awards_source_type CHECK (source_type IN ('AFFILIATION_RULE', 'MANUAL', 'METRIC')),
    CONSTRAINT ck_profile_badge_awards_visibility CHECK (visibility IN ('SHOWN', 'HIDDEN'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_profile_badge_awards_active_profile_type
    ON profile_badge_awards (profile_id, badge_type_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_profile_badge_awards_primary
    ON profile_badge_awards (profile_id)
    WHERE status = 'ACTIVE' AND visibility = 'SHOWN' AND is_primary = true;

CREATE INDEX IF NOT EXISTS idx_profile_badge_awards_profile
    ON profile_badge_awards (profile_id, status, visibility, awarded_at DESC);

CREATE INDEX IF NOT EXISTS idx_profile_badge_awards_badge_type
    ON profile_badge_awards (badge_type_id, status);

CREATE TABLE IF NOT EXISTS badge_affiliation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    profile_role VARCHAR(40) NOT NULL,
    badge_type_id UUID NOT NULL REFERENCES badge_types(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_badge_affiliation_rules_role CHECK (profile_role IN ('MENTOR', 'MENTEE', 'EMPLOYEE')),
    CONSTRAINT ck_badge_affiliation_rules_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_badge_affiliation_rules_company_role_active
    ON badge_affiliation_rules (company_id, profile_role)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_badge_affiliation_rules_badge_type
    ON badge_affiliation_rules (badge_type_id, status);

CREATE TABLE IF NOT EXISTS badge_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    badge_type_id UUID REFERENCES badge_types(id),
    award_id UUID REFERENCES profile_badge_awards(id) ON DELETE SET NULL,
    profile_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
    actor_id UUID,
    event_type VARCHAR(40) NOT NULL,
    reason TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_badge_audit_events_event_type CHECK (
        event_type IN (
            'BADGE_TYPE_CREATED',
            'BADGE_TYPE_UPDATED',
            'BADGE_TYPE_RETIRED',
            'AFFILIATION_RULE_CREATED',
            'AFFILIATION_RULE_UPDATED',
            'BADGE_AWARDED',
            'BADGE_REVOKED',
            'BADGE_VISIBILITY_CHANGED',
            'BADGE_PRIMARY_CHANGED'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_badge_audit_events_profile
    ON badge_audit_events (profile_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_badge_audit_events_badge_type
    ON badge_audit_events (badge_type_id, created_at DESC);

COMMENT ON TABLE badge_types IS 'Configurable badge definitions available across Prosper Mentor';
COMMENT ON TABLE profile_badge_awards IS 'Badges awarded to user profiles with visibility and primary selection state';
COMMENT ON TABLE badge_affiliation_rules IS 'Opt-in company affiliation rules that auto-award badges by role';
COMMENT ON TABLE badge_audit_events IS 'Append-only audit trail for badge governance and profile badge changes';
