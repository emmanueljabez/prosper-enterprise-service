CREATE TABLE IF NOT EXISTS company_program_cohorts (
    id UUID PRIMARY KEY,
    company_program_id UUID NOT NULL REFERENCES company_programs(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(80) NOT NULL,
    chapter VARCHAR(160),
    region VARCHAR(160),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    starts_at TIMESTAMP,
    ends_at TIMESTAMP,
    self_join_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    self_join_code_hash VARCHAR(255),
    self_join_expires_at TIMESTAMP,
    self_join_capacity INTEGER,
    circle_min_size INTEGER NOT NULL DEFAULT 5,
    circle_max_size INTEGER NOT NULL DEFAULT 10,
    interest_tag_set JSONB NOT NULL DEFAULT '[]'::jsonb,
    plenary_event_type VARCHAR(40),
    plenary_event_id VARCHAR(160),
    matching_starts_after_circles_finalized BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_program_cohorts_status CHECK (status IN (
        'DRAFT', 'INTAKE_OPEN', 'INTAKE_CLOSED', 'PLENARY_SCHEDULED',
        'CIRCLES_FORMING', 'CIRCLES_FINALIZED', 'MATCHING', 'ACTIVE',
        'COMPLETED', 'CANCELLED', 'ARCHIVED'
    )),
    CONSTRAINT chk_company_program_cohorts_plenary_event_type CHECK (
        plenary_event_type IS NULL OR plenary_event_type IN ('SUMMIT_EVENT', 'EXTERNAL_EVENT', 'MANUAL_EVENT')
    ),
    CONSTRAINT chk_company_program_cohorts_circle_sizes CHECK (
        circle_min_size > 0 AND circle_max_size >= circle_min_size
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_company_program_cohorts_program_code
    ON company_program_cohorts(company_program_id, lower(code));
CREATE INDEX IF NOT EXISTS idx_company_program_cohorts_program_status
    ON company_program_cohorts(company_program_id, status);

CREATE TABLE IF NOT EXISTS company_program_cohort_join_requests (
    id UUID PRIMARY KEY,
    company_program_cohort_id UUID NOT NULL REFERENCES company_program_cohorts(id) ON DELETE CASCADE,
    submitted_email VARCHAR(255) NOT NULL,
    submitted_phone VARCHAR(80),
    submitted_first_name VARCHAR(160),
    submitted_last_name VARCHAR(160),
    submitted_chapter VARCHAR(160),
    submitted_region VARCHAR(160),
    submitted_interest_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    matched_profile_id UUID REFERENCES profiles(id),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_company_program_cohort_join_requests_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'DUPLICATE_REVIEW', 'EXPIRED')
    )
);

CREATE INDEX IF NOT EXISTS idx_cohort_join_requests_cohort_status
    ON company_program_cohort_join_requests(company_program_cohort_id, status);
CREATE INDEX IF NOT EXISTS idx_cohort_join_requests_email
    ON company_program_cohort_join_requests(lower(submitted_email));

CREATE TABLE IF NOT EXISTS company_program_cohort_participants (
    id UUID PRIMARY KEY,
    company_program_cohort_id UUID NOT NULL REFERENCES company_program_cohorts(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id),
    company_program_participant_id UUID REFERENCES company_program_participants(id),
    source VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    first_name_snapshot VARCHAR(160),
    last_name_snapshot VARCHAR(160),
    email_snapshot VARCHAR(255),
    phone_snapshot VARCHAR(80),
    chapter VARCHAR(160),
    region VARCHAR(160),
    interest_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    self_join_request_id UUID REFERENCES company_program_cohort_join_requests(id),
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMP,
    duplicate_status VARCHAR(40) NOT NULL DEFAULT 'CLEAR',
    duplicate_candidate_profile_id UUID REFERENCES profiles(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_program_cohort_id, profile_id),
    CONSTRAINT chk_cohort_participants_source CHECK (
        source IN ('ROSTER_UPLOAD', 'MANUAL_ADD', 'SELF_JOIN', 'ADMIN_TRANSFER')
    ),
    CONSTRAINT chk_cohort_participants_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'PLENARY_ATTENDED', 'PLACED_IN_CIRCLE',
                   'ELIGIBLE_FOR_MATCHING', 'MATCHED', 'ACTIVE', 'COMPLETED',
                   'WITHDRAWN', 'REJECTED')
    ),
    CONSTRAINT chk_cohort_participants_duplicate_status CHECK (
        duplicate_status IN ('CLEAR', 'POSSIBLE_DUPLICATE', 'RESOLVED_EXISTING_PROFILE', 'RESOLVED_NEW_PROFILE')
    )
);

CREATE INDEX IF NOT EXISTS idx_cohort_participants_cohort_status
    ON company_program_cohort_participants(company_program_cohort_id, status);
CREATE INDEX IF NOT EXISTS idx_cohort_participants_program_participant
    ON company_program_cohort_participants(company_program_participant_id);

CREATE TABLE IF NOT EXISTS company_program_cohort_plenary_attendance (
    id UUID PRIMARY KEY,
    company_program_cohort_id UUID NOT NULL REFERENCES company_program_cohorts(id) ON DELETE CASCADE,
    cohort_participant_id UUID NOT NULL REFERENCES company_program_cohort_participants(id) ON DELETE CASCADE,
    attendance_source VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REGISTERED',
    attended_at TIMESTAMP,
    recorded_by_user_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_program_cohort_id, cohort_participant_id),
    CONSTRAINT chk_cohort_plenary_attendance_source CHECK (
        attendance_source IN ('SUMMIT_EVENT', 'IMPORT', 'ADMIN_OVERRIDE')
    ),
    CONSTRAINT chk_cohort_plenary_attendance_status CHECK (
        status IN ('REGISTERED', 'ATTENDED', 'ABSENT', 'EXCUSED')
    )
);

CREATE TABLE IF NOT EXISTS common_interest_circles (
    id UUID PRIMARY KEY,
    company_program_cohort_id UUID NOT NULL REFERENCES company_program_cohorts(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    theme VARCHAR(255),
    interest_tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    facilitator_profile_id UUID REFERENCES profiles(id),
    min_size INTEGER NOT NULL DEFAULT 5,
    max_size INTEGER NOT NULL DEFAULT 10,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    next_session_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_common_interest_circles_status CHECK (
        status IN ('DRAFT', 'FORMING', 'FINALIZED', 'ACTIVE', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_common_interest_circles_sizes CHECK (min_size > 0 AND max_size >= min_size)
);

CREATE INDEX IF NOT EXISTS idx_common_interest_circles_cohort_status
    ON common_interest_circles(company_program_cohort_id, status);

CREATE TABLE IF NOT EXISTS common_interest_circle_memberships (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL REFERENCES common_interest_circles(id) ON DELETE CASCADE,
    cohort_participant_id UUID NOT NULL REFERENCES company_program_cohort_participants(id) ON DELETE CASCADE,
    placement_source VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PLACED',
    placed_by_user_id UUID,
    placed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (circle_id, cohort_participant_id),
    CONSTRAINT chk_circle_memberships_placement_source CHECK (
        placement_source IN ('SUGGESTED', 'MENTEE_REQUESTED', 'ADMIN_PLACED', 'ADMIN_MOVED')
    ),
    CONSTRAINT chk_circle_memberships_status CHECK (
        status IN ('PENDING_REQUEST', 'PLACED', 'REMOVED', 'COMPLETED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_active_circle_membership_per_cohort_participant
    ON common_interest_circle_memberships(cohort_participant_id)
    WHERE status IN ('PENDING_REQUEST', 'PLACED');

CREATE TABLE IF NOT EXISTS common_interest_circle_notes (
    id UUID PRIMARY KEY,
    circle_id UUID NOT NULL REFERENCES common_interest_circles(id) ON DELETE CASCADE,
    cohort_participant_id UUID REFERENCES company_program_cohort_participants(id) ON DELETE CASCADE,
    author_profile_id UUID REFERENCES profiles(id),
    note_type VARCHAR(40) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_circle_notes_note_type CHECK (
        note_type IN ('FACILITATOR_NOTE', 'COMPLETION_NOTE', 'ADMIN_NOTE')
    )
);
