CREATE TABLE community_categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug varchar(80) NOT NULL,
    name varchar(120) NOT NULL,
    description text,
    sort_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uniq_community_categories_slug UNIQUE (slug)
);

CREATE TABLE community_posts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    author_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    category_id uuid REFERENCES community_categories(id) ON DELETE SET NULL,
    content text NOT NULL,
    visibility varchar(40) NOT NULL DEFAULT 'PUBLIC',
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    moderation_status varchar(40) NOT NULL DEFAULT 'APPROVED',
    media_url text,
    media_type varchar(80),
    image_url text,
    link_url text,
    link_title text,
    link_description text,
    link_image text,
    hashtags text[] NOT NULL DEFAULT ARRAY[]::text[],
    likes_count integer NOT NULL DEFAULT 0,
    comments_count integer NOT NULL DEFAULT 0,
    saves_count integer NOT NULL DEFAULT 0,
    reports_count integer NOT NULL DEFAULT 0,
    pinned_at timestamp,
    pinned_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    deleted_at timestamp,
    CONSTRAINT chk_community_posts_visibility CHECK (visibility IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    CONSTRAINT chk_community_posts_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT chk_community_posts_moderation_status CHECK (moderation_status IN ('APPROVED', 'PENDING_REVIEW', 'REJECTED')),
    CONSTRAINT chk_community_posts_content_present CHECK (length(trim(content)) > 0),
    CONSTRAINT chk_community_posts_counters_non_negative CHECK (
        likes_count >= 0
        AND comments_count >= 0
        AND saves_count >= 0
        AND reports_count >= 0
    )
);

CREATE INDEX idx_community_posts_author_created
    ON community_posts(author_profile_id, created_at DESC);

CREATE INDEX idx_community_posts_feed
    ON community_posts(status, moderation_status, visibility, created_at DESC);

CREATE INDEX idx_community_posts_category
    ON community_posts(category_id, created_at DESC)
    WHERE status = 'ACTIVE';

CREATE TABLE community_post_reactions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    user_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    reaction_type varchar(40) NOT NULL DEFAULT 'LIKE',
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_post_reactions_type CHECK (reaction_type IN ('LIKE'))
);

CREATE UNIQUE INDEX uniq_community_post_reactions_user_type
    ON community_post_reactions(post_id, user_profile_id, reaction_type);

CREATE INDEX idx_community_post_reactions_user
    ON community_post_reactions(user_profile_id, created_at DESC);

CREATE TABLE community_post_comments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    parent_comment_id uuid REFERENCES community_post_comments(id) ON DELETE CASCADE,
    content text NOT NULL,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    deleted_at timestamp,
    CONSTRAINT chk_community_post_comments_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'DELETED')),
    CONSTRAINT chk_community_post_comments_content_present CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_community_post_comments_post_created
    ON community_post_comments(post_id, created_at ASC);

CREATE INDEX idx_community_post_comments_author
    ON community_post_comments(author_profile_id, created_at DESC);

CREATE TABLE community_saved_posts (
    post_id uuid NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    user_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT pk_community_saved_posts PRIMARY KEY (post_id, user_profile_id)
);

CREATE INDEX idx_community_saved_posts_user
    ON community_saved_posts(user_profile_id, created_at DESC);

CREATE TABLE community_blocks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    blocked_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    reason text,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_blocks_not_self CHECK (blocker_profile_id <> blocked_profile_id)
);

CREATE UNIQUE INDEX uniq_community_blocks_pair
    ON community_blocks(blocker_profile_id, blocked_profile_id);

CREATE INDEX idx_community_blocks_blocked
    ON community_blocks(blocked_profile_id, blocker_profile_id);

CREATE TABLE community_reports (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_type varchar(40) NOT NULL,
    target_id uuid NOT NULL,
    reason_code varchar(80) NOT NULL,
    reason_detail text,
    status varchar(40) NOT NULL DEFAULT 'OPEN',
    reviewed_by_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    reviewed_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_reports_target_type CHECK (target_type IN ('POST', 'COMMENT', 'PROFILE')),
    CONSTRAINT chk_community_reports_status CHECK (status IN ('OPEN', 'REVIEWED', 'DISMISSED', 'ACTIONED')),
    CONSTRAINT chk_community_reports_reason_present CHECK (length(trim(reason_code)) > 0)
);

CREATE UNIQUE INDEX uniq_community_open_report
    ON community_reports(reporter_profile_id, target_type, target_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_community_reports_status_created
    ON community_reports(status, created_at DESC);

CREATE INDEX idx_community_reports_target
    ON community_reports(target_type, target_id);

CREATE TABLE community_mentions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type varchar(40) NOT NULL,
    content_id uuid NOT NULL,
    mentioned_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    mentioning_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    mention_text varchar(160),
    mention_position integer,
    created_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_mentions_content_type CHECK (content_type IN ('POST', 'COMMENT'))
);

CREATE INDEX idx_community_mentions_mentioned
    ON community_mentions(mentioned_profile_id, created_at DESC);

CREATE INDEX idx_community_mentions_content
    ON community_mentions(content_type, content_id);

CREATE TABLE community_notification_preferences (
    profile_id uuid PRIMARY KEY REFERENCES profiles(id) ON DELETE CASCADE,
    in_app_enabled boolean NOT NULL DEFAULT true,
    email_enabled boolean NOT NULL DEFAULT true,
    whatsapp_enabled boolean NOT NULL DEFAULT false,
    mentions_enabled boolean NOT NULL DEFAULT true,
    comments_enabled boolean NOT NULL DEFAULT true,
    reactions_enabled boolean NOT NULL DEFAULT true,
    connections_enabled boolean NOT NULL DEFAULT true,
    recommendations_enabled boolean NOT NULL DEFAULT true,
    digest_frequency varchar(40) NOT NULL DEFAULT 'DAILY',
    quiet_hours_start time,
    quiet_hours_end time,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_notification_digest_frequency CHECK (digest_frequency IN ('IMMEDIATE', 'DAILY', 'WEEKLY', 'NEVER'))
);

CREATE TABLE community_events_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type varchar(120) NOT NULL,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    actor_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    recipient_profile_id uuid REFERENCES profiles(id) ON DELETE SET NULL,
    payload_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(40) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp,
    published_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT chk_community_events_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_community_events_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_community_events_outbox_pending
    ON community_events_outbox(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_community_events_outbox_recipient
    ON community_events_outbox(recipient_profile_id, status, created_at DESC)
    WHERE recipient_profile_id IS NOT NULL;

CREATE INDEX idx_community_events_outbox_aggregate
    ON community_events_outbox(aggregate_type, aggregate_id, created_at DESC);

INSERT INTO community_categories (slug, name, description, sort_order, is_active)
VALUES
    ('career-growth', 'Career Growth', 'Career advice, transitions, and practical growth questions.', 10, true),
    ('leadership', 'Leadership', 'Leadership lessons, management challenges, and team development.', 20, true),
    ('industry-insights', 'Industry Insights', 'Industry trends, market context, and domain-specific learning.', 30, true),
    ('mentorship', 'Mentorship', 'Mentoring questions, success stories, and guidance for better sessions.', 40, true),
    ('wins', 'Wins', 'Member wins, milestones, and progress worth sharing.', 50, true),
    ('questions', 'Questions', 'Open questions for the ProsperMentor community.', 60, true)
ON CONFLICT (slug) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    updated_at = now();
