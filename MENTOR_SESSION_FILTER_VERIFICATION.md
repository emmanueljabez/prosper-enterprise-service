# MENTOR_SESSION Feature Filter Verification Guide

## Issue Description
You mentioned seeing plans without MENTOR_SESSION being returned when a user creates a session request. This guide helps verify and fix that issue.

## How the Filter Works

The code at `SubscriptionService.getRecommendedPlansWithMentorSessions()` filters plans with these criteria:

```java
.filter(plan -> plan.getPlanFeatures().stream()
    .anyMatch(pf -> pf.getFeature().getType() == Feature.FeatureType.MENTOR_SESSION
                 && pf.getEnabled()
                 && pf.isAvailable()))
```

### What This Checks:
1. **Feature Type**: Must be `MENTOR_SESSION` (not CONTENT_ACCESS, EVENT_ACCESS, or COMMUNITY)
2. **Enabled**: The `enabled` flag must be `true`
3. **Available**: The feature must be available (`isAvailable()` checks: `enabled && (limitValue > 0 || isUnlimited())`)

## Possible Causes of the Issue

### 1. Database Configuration Issue (Most Likely)
Your database might have plans configured incorrectly:

**Check your `subscription_plans` and `plan_features` tables:**

```sql
-- See all plans and their features
SELECT
    sp.name AS plan_name,
    sp.is_active AS plan_active,
    f.name AS feature_name,
    f.type AS feature_type,
    pf.enabled AS feature_enabled,
    pf.limit_value AS feature_limit
FROM subscription_plans sp
LEFT JOIN plan_features pf ON sp.id = pf.plan_id
LEFT JOIN features f ON pf.feature_id = f.id
ORDER BY sp.display_order, f.type;
```

**Common problems:**
- Plans exist but have NO features defined
- Plans have features but `enabled = false`
- Plans have MENTOR_SESSION but `limit_value = 0`
- Feature type is wrong (e.g., `CONTENT_ACCESS` instead of `MENTOR_SESSION`)

### 2. Empty PlanFeatures List
If a plan's `planFeatures` collection is empty or not loaded properly:
- The `anyMatch()` will return `false` (correct)
- But if there's a lazy loading issue, features might not be fetched

### 3. Feature Type Mismatch
If features in the database have the wrong type value:
```sql
-- Check feature types in database
SELECT id, code, name, type FROM features WHERE type = 'MENTOR_SESSION';
```

## How to Verify the Issue

### Step 1: Check the Logs
With the enhanced logging, when you try to book a session, you should see:

```
INFO  - Finding recommended plans with MENTOR_SESSION feature from 5 active plans. Current plan ID to exclude: abc-123
DEBUG - Evaluating plan 'Basic' for MENTOR_SESSION feature. Plan has 3 features.
DEBUG -   Feature 'Network Access' (type: COMMUNITY): enabled=true, available=true, matches=false
DEBUG -   Feature 'YouTube Channel' (type: CONTENT_ACCESS): enabled=true, available=true, matches=false
DEBUG -   Feature 'Learn Content' (type: CONTENT_ACCESS): enabled=true, available=true, matches=false
INFO  - ✗ Plan 'Basic' does NOT include MENTOR_SESSION feature or it's disabled/unavailable - NOT recommending
DEBUG - Evaluating plan 'Premium' for MENTOR_SESSION feature. Plan has 5 features.
DEBUG -   Feature '1:1 Mentor Sessions' (type: MENTOR_SESSION): enabled=true, available=true, matches=true
INFO  - ✓ Plan 'Premium' includes MENTOR_SESSION feature and WILL be recommended
INFO  - Found 1 recommended plans with one-on-one mentor session feature: Premium
```

### Step 2: Check Database Configuration

Run this query to see which plans have MENTOR_SESSION:

```sql
SELECT
    sp.id,
    sp.name,
    sp.code,
    sp.is_active,
    f.type,
    pf.enabled,
    pf.limit_value,
    CASE
        WHEN pf.enabled = true AND (pf.limit_value > 0 OR pf.limit_value = -1) THEN 'WILL BE RECOMMENDED'
        ELSE 'NOT RECOMMENDED'
    END as recommendation_status
FROM subscription_plans sp
LEFT JOIN plan_features pf ON sp.id = pf.plan_id
LEFT JOIN features f ON pf.feature_id = f.id
WHERE f.type = 'MENTOR_SESSION'
    AND sp.is_active = true
ORDER BY sp.display_order;
```

### Step 3: Verify API Response

Make a request that triggers ineligibility and check the `recommendedPlans` in the response:

```bash
curl -X POST http://localhost:8080/api/sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "menteeId": "user-with-basic-plan",
    "mentorId": "some-mentor",
    "skillId": "some-skill",
    "scheduledStart": "2025-11-01T10:00:00Z"
  }'
```

**Expected Response (if user can't book):**
```json
{
  "status": "error",
  "message": "Your current 'Basic' plan does not include 1:1 mentor sessions...",
  "data": {
    "recommendedPlans": [
      {
        "name": "Premium",
        "code": "PREMIUM",
        "sessionsPerPeriod": 5
      }
    ]
  }
}
```

## How to Fix Database Issues

### Fix 1: Ensure Features Exist
```sql
-- Create MENTOR_SESSION feature if it doesn't exist
INSERT INTO features (id, code, name, description, type, is_active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'MENTOR_SESSION_1_ON_1',
    '1:1 Mentor Sessions',
    'Access to one-on-one mentor sessions',
    'MENTOR_SESSION',
    true,
    NOW(),
    NOW()
);
```

### Fix 2: Add MENTOR_SESSION to Plans
```sql
-- Add MENTOR_SESSION feature to Premium plan
INSERT INTO plan_features (id, plan_id, feature_id, limit_value, enabled, created_at)
SELECT
    gen_random_uuid(),
    sp.id,
    f.id,
    5,  -- 5 sessions per month
    true,
    NOW()
FROM subscription_plans sp
CROSS JOIN features f
WHERE sp.code = 'PREMIUM'
    AND f.type = 'MENTOR_SESSION'
    AND NOT EXISTS (
        SELECT 1 FROM plan_features pf2
        WHERE pf2.plan_id = sp.id AND pf2.feature_id = f.id
    );
```

### Fix 3: Enable Existing Features
```sql
-- Enable MENTOR_SESSION features that are disabled
UPDATE plan_features pf
SET enabled = true, limit_value = 5
FROM features f
WHERE pf.feature_id = f.id
    AND f.type = 'MENTOR_SESSION'
    AND pf.enabled = false;
```

## Expected Behavior

### Scenario 1: User with "Basic" plan (no MENTOR_SESSION) tries to book
- API returns: `FEATURE_NOT_AVAILABLE`
- Recommended plans: ["Premium", "Professional"] (only plans with MENTOR_SESSION)

### Scenario 2: User with "Premium" plan (has MENTOR_SESSION) exhausts sessions
- API returns: `SESSIONS_EXHAUSTED`
- Recommended plans: ["Professional"] (higher tier plan with more/unlimited sessions, excluding Premium)

### Scenario 3: User has no subscription
- API returns: `NO_ACTIVE_SUBSCRIPTION`
- Recommended plans: All plans with MENTOR_SESSION feature

## Testing Checklist

- [ ] Check logs when booking fails - verify which plans are being evaluated
- [ ] Verify database has plans with `type = 'MENTOR_SESSION'`
- [ ] Verify those features have `enabled = true`
- [ ] Verify those features have `limit_value > 0` or `limit_value = -1`
- [ ] Verify API response includes only correct plans
- [ ] Test with different user scenarios (no sub, expired sub, wrong plan, exhausted sessions)

## Contact Points in Code

- Filter logic: `SubscriptionService.java:199-228`
- Feature type enum: `Feature.java:86-91`
- PlanFeature availability: `PlanFeature.java:95-97`
- API error handling: `SessionController.java:77-90`
