-- Restore Enterprise Standard corporate pricing after temporary test pricing.
-- Also normalize current company wallet display snapshots that still show KES 1.00.

UPDATE subscription_plans
SET name = 'Enterprise Standard',
    cost = 4500.00,
    currency = 'KES'
WHERE code = 'GROWTH_PLUS'
  AND plan_audience IN ('CORPORATE', 'BOTH');

UPDATE company_session_wallets w
SET price_per_session_snapshot = 4500.00,
    updated_at = CURRENT_TIMESTAMP
FROM company_subscriptions cs
JOIN subscription_plans sp ON sp.id = cs.plan_id
WHERE w.company_subscription_id = cs.id
  AND sp.code = 'GROWTH_PLUS'
  AND w.price_per_session_snapshot = 1.00;
