-- Seed public B2C mentorship session packages used by /pricing and booking payment dialogs.
-- These are one-time package purchases, distinct from legacy recurring access tiers.

INSERT INTO features (code, name, description, type, is_active)
VALUES (
    'ONE_ON_ONE',
    'Prosper Mentor 1:1 Sessions',
    'Live, personalized guidance from trusted mentors',
    'MENTOR_SESSION',
    true
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO subscription_plans (
    name,
    code,
    description,
    cost,
    currency,
    sessions_per_period,
    duration_months,
    is_active,
    display_order,
    features,
    billing_type,
    allows_addons,
    addon_session_cost,
    plan_audience,
    min_seats,
    default_seats,
    max_seats
)
VALUES
    (
        'Single Session',
        'SINGLE_SESSION',
        '1 one-on-one session',
        4000.00,
        'KES',
        1,
        1,
        true,
        10,
        '45-minute focused call,Immediate session notes,Direct chat (24h)',
        'ONE_TIME',
        false,
        NULL,
        'INDIVIDUAL',
        1,
        1,
        NULL
    ),
    (
        '3-Session Pack',
        'PACK_3',
        'Structured guidance',
        11000.00,
        'KES',
        3,
        1,
        true,
        11,
        'Milestone planning,Document reviews,Priority booking',
        'ONE_TIME',
        false,
        NULL,
        'INDIVIDUAL',
        1,
        1,
        NULL
    ),
    (
        '5-Session Pack',
        'PACK_5',
        'Consistent growth path',
        17500.00,
        'KES',
        5,
        1,
        true,
        12,
        'Monthly career audits,Long-term goal setting,Priority scheduling',
        'ONE_TIME',
        false,
        NULL,
        'INDIVIDUAL',
        1,
        1,
        NULL
    ),
    (
        '10-Session Pack',
        'PACK_10',
        'Maximized commitment',
        33000.00,
        'KES',
        10,
        1,
        true,
        13,
        'Unlimited chat support,Personalized roadmap,Intro to network',
        'ONE_TIME',
        false,
        NULL,
        'INDIVIDUAL',
        1,
        1,
        NULL
    )
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    cost = EXCLUDED.cost,
    currency = EXCLUDED.currency,
    sessions_per_period = EXCLUDED.sessions_per_period,
    duration_months = EXCLUDED.duration_months,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order,
    features = EXCLUDED.features,
    billing_type = EXCLUDED.billing_type,
    allows_addons = EXCLUDED.allows_addons,
    addon_session_cost = EXCLUDED.addon_session_cost,
    plan_audience = EXCLUDED.plan_audience,
    min_seats = EXCLUDED.min_seats,
    default_seats = EXCLUDED.default_seats,
    max_seats = EXCLUDED.max_seats,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
SELECT
    sp.id,
    f.id,
    sp.sessions_per_period,
    true
FROM subscription_plans sp
CROSS JOIN features f
WHERE sp.code IN ('SINGLE_SESSION', 'PACK_3', 'PACK_5', 'PACK_10')
  AND f.code = 'ONE_ON_ONE'
  AND NOT EXISTS (
      SELECT 1
      FROM plan_features pf
      WHERE pf.plan_id = sp.id
        AND pf.feature_id = f.id
  );

UPDATE plan_features pf
SET limit_value = sp.sessions_per_period,
    enabled = true
FROM subscription_plans sp
JOIN features f ON f.code = 'ONE_ON_ONE'
WHERE pf.plan_id = sp.id
  AND pf.feature_id = f.id
  AND sp.code IN ('SINGLE_SESSION', 'PACK_3', 'PACK_5', 'PACK_10');
