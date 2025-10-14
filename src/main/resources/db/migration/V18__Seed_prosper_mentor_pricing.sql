-- V18__Seed_prosper_mentor_pricing.sql
-- Seed data for Prosper Mentor tiered pricing structure
-- Uses dynamic lookups instead of hardcoded UUIDs for compatibility

-- ========== INSERT FEATURES ==========

-- Insert base features (let database generate IDs)
INSERT INTO features (code, name, description, type, is_active) VALUES
    ('NETWORK', 'Prosper Mentor Network', 'Community of mentors and mentees where mentorship comes alive', 'COMMUNITY', true),
    ('YOUTUBE', 'Prosper Mentor YouTube Channel', 'Access to YouTube content and resources', 'CONTENT_ACCESS', true),
    ('LEARN', 'Prosper Mentor Learn', 'Curated growth content library for Africa''s rising generation', 'CONTENT_ACCESS', true),
    ('SUMMIT', 'Prosper Mentor Summit', 'VIP access to exclusive summit recordings and transformative wisdom', 'EVENT_ACCESS', true),
    ('VIRTUAL_MENTOR', 'Prosper Mentor Virtual', '24/7 interactive virtual mentor - guidance when you need it, how you need it', 'MENTOR_SESSION', true),
    ('ONE_ON_ONE', 'Prosper Mentor 1:1 Sessions', 'Live, personalized guidance from trusted mentors', 'MENTOR_SESSION', true)
ON CONFLICT (code) DO NOTHING;

-- ========== CREATE SUBSCRIPTION PLANS ==========

-- Plan 1: Network (Free)
INSERT INTO subscription_plans (name, code, description, cost, currency, sessions_per_period, duration_months, is_active, display_order, billing_type, allows_addons, addon_session_cost)
VALUES (
    'Network',
    'NETWORK',
    'Community of mentors and mentees',
    0.00,
    'KES',
    0,
    1,
    true,
    1,
    'FREE',
    false,
    NULL
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    sessions_per_period = EXCLUDED.sessions_per_period,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    display_order = EXCLUDED.display_order;

-- Plan 2: Learn ($5/Month or $50/Year)
INSERT INTO subscription_plans (name, code, description, cost, currency, sessions_per_period, duration_months, is_active, display_order, billing_type, yearly_cost, allows_addons, addon_session_cost)
VALUES (
    'Learn',
    'LEARN',
    'Courses, curated content library',
    5.00,
    'USD',
    0,
    1,
    true,
    2,
    'RECURRING',
    50.00,
    false,
    NULL
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    yearly_cost = EXCLUDED.yearly_cost,
    sessions_per_period = EXCLUDED.sessions_per_period,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    display_order = EXCLUDED.display_order;

-- Plan 3: Summit ($8/Summit - One-time)
INSERT INTO subscription_plans (name, code, description, cost, currency, sessions_per_period, duration_months, is_active, display_order, billing_type, allows_addons, addon_session_cost)
VALUES (
    'Summit',
    'SUMMIT',
    'VIP Access to summit recordings',
    8.00,
    'USD',
    0,
    12,
    true,
    3,
    'ONE_TIME',
    false,
    NULL
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    sessions_per_period = EXCLUDED.sessions_per_period,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    display_order = EXCLUDED.display_order,
    duration_months = EXCLUDED.duration_months;

-- Plan 4: Virtual Mentor ($10/Month or $100/Year)
INSERT INTO subscription_plans (name, code, description, cost, currency, sessions_per_period, duration_months, is_active, display_order, billing_type, yearly_cost, allows_addons, addon_session_cost)
VALUES (
    'Virtual Mentor',
    'VIRTUAL_MENTOR',
    '24/7 interactive, virtual mentor',
    10.00,
    'USD',
    0,
    1,
    true,
    4,
    'RECURRING',
    100.00,
    false,
    NULL
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    yearly_cost = EXCLUDED.yearly_cost,
    sessions_per_period = EXCLUDED.sessions_per_period,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    display_order = EXCLUDED.display_order;

-- Plan 5: All Access ($30/Month or $300/Year)
INSERT INTO subscription_plans (name, code, description, cost, currency, sessions_per_period, duration_months, is_active, display_order, billing_type, yearly_cost, allows_addons, addon_session_cost)
VALUES (
    'All Access',
    'ALL_ACCESS',
    'Monthly 1:1 Sessions with live, personalized guidance from trusted mentors',
    30.00,
    'USD',
    1,
    1,
    true,
    5,
    'RECURRING',
    300.00,
    true,
    20.00
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    yearly_cost = EXCLUDED.yearly_cost,
    sessions_per_period = EXCLUDED.sessions_per_period,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    addon_session_cost = EXCLUDED.addon_session_cost,
    display_order = EXCLUDED.display_order;

-- ========== MAP FEATURES TO PLANS ==========
-- Using subqueries to dynamically fetch IDs by code

-- Network Plan Features (Free Tier)
-- Includes: Network, YouTube
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'NETWORK'),
    (SELECT id FROM features WHERE code = 'NETWORK'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'NETWORK')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'NETWORK')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'NETWORK'),
    (SELECT id FROM features WHERE code = 'YOUTUBE'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'NETWORK')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'YOUTUBE')
);

-- Learn Plan Features ($5/month)
-- Includes: Network, YouTube, Learn
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'LEARN'),
    (SELECT id FROM features WHERE code = 'NETWORK'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'LEARN')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'NETWORK')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'LEARN'),
    (SELECT id FROM features WHERE code = 'YOUTUBE'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'LEARN')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'YOUTUBE')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'LEARN'),
    (SELECT id FROM features WHERE code = 'LEARN'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'LEARN')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'LEARN')
);

-- Summit Plan Features ($8/summit)
-- Includes: Network, YouTube, Learn, Summit
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'SUMMIT'),
    (SELECT id FROM features WHERE code = 'NETWORK'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'SUMMIT')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'NETWORK')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'SUMMIT'),
    (SELECT id FROM features WHERE code = 'YOUTUBE'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'SUMMIT')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'YOUTUBE')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'SUMMIT'),
    (SELECT id FROM features WHERE code = 'LEARN'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'SUMMIT')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'LEARN')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'SUMMIT'),
    (SELECT id FROM features WHERE code = 'SUMMIT'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'SUMMIT')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'SUMMIT')
);

-- Virtual Mentor Plan Features ($10/month)
-- Includes: Network, YouTube, Learn, Summit, Virtual Mentor
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR'),
    (SELECT id FROM features WHERE code = 'NETWORK'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'NETWORK')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR'),
    (SELECT id FROM features WHERE code = 'YOUTUBE'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'YOUTUBE')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR'),
    (SELECT id FROM features WHERE code = 'LEARN'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'LEARN')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR'),
    (SELECT id FROM features WHERE code = 'SUMMIT'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'SUMMIT')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR'),
    (SELECT id FROM features WHERE code = 'VIRTUAL_MENTOR'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'VIRTUAL_MENTOR')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'VIRTUAL_MENTOR')
);

-- All Access Plan Features ($30/month)
-- Includes: Everything (Network, YouTube, Learn, Summit, Virtual Mentor, 1:1 Sessions)
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'NETWORK'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'NETWORK')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'YOUTUBE'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'YOUTUBE')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'LEARN'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'LEARN')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'SUMMIT'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'SUMMIT')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'VIRTUAL_MENTOR'),
    -1,
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'VIRTUAL_MENTOR')
);

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS'),
    (SELECT id FROM features WHERE code = 'ONE_ON_ONE'),
    1,  -- 1 session per month
    true
WHERE NOT EXISTS (
    SELECT 1 FROM plan_features pf
    WHERE pf.plan_id = (SELECT id FROM subscription_plans WHERE code = 'ALL_ACCESS')
    AND pf.feature_id = (SELECT id FROM features WHERE code = 'ONE_ON_ONE')
);

-- Add comments for clarity
COMMENT ON TABLE features IS 'Available features across all subscription tiers';
COMMENT ON TABLE plan_features IS 'Maps features to subscription plans with limits. -1 = unlimited, 0 = not available, N = specific limit';
COMMENT ON TABLE subscription_addons IS 'Additional purchases like extra 1:1 sessions beyond subscription limits';
