# Dynamic Feature-Based Subscription System

## Overview

The Prosper Mentor subscription system has been enhanced with a **dynamic feature-based architecture** that allows for flexible pricing tiers and feature management without requiring code changes.

## Architecture

### Core Entities

#### 1. **Feature** ([Feature.java](src/main/java/com/prosper/prospermentor/entity/Feature.java))
Represents individual features/benefits available across subscription tiers.

**Properties:**
- `code`: Unique identifier (e.g., "NETWORK", "LEARN", "SUMMIT")
- `name`: Display name
- `description`: Feature description
- `type`: FeatureType enum (CONTENT_ACCESS, MENTOR_SESSION, EVENT_ACCESS, COMMUNITY)
- `isActive`: Whether feature is currently available

**Example Features:**
- NETWORK - Prosper Mentor Network (Community)
- YOUTUBE - YouTube Channel Access
- LEARN - Curated Content Library
- SUMMIT - Summit Recordings
- VIRTUAL_MENTOR - 24/7 Virtual Mentor
- ONE_ON_ONE - 1:1 Mentor Sessions

#### 2. **PlanFeature** ([PlanFeature.java](src/main/java/com/prosper/prospermentor/entity/PlanFeature.java))
Junction entity linking SubscriptionPlans to Features with limits.

**Properties:**
- `plan`: The subscription plan
- `feature`: The feature being granted
- `limitValue`: Usage limit
  - `-1` = Unlimited
  - `0` = Not available
  - `N` = Specific quantity/limit
- `enabled`: Whether feature is currently enabled
- `metadata`: JSON for additional configuration

#### 3. **SubscriptionPlan** (Enhanced - [SubscriptionPlan.java](src/main/java/com/prosper/prospermentor/entity/SubscriptionPlan.java))
Enhanced with new properties:

**New Properties:**
- `billingType`: BillingType enum (RECURRING, ONE_TIME, FREE)
- `yearlyCost`: Cost for yearly billing option
- `allowsAddons`: Whether plan supports purchasing add-ons
- `addonSessionCost`: Cost per additional session
- `planFeatures`: List of PlanFeature relationships

**New Methods:**
- `hasFeature(String featureCode)`: Check if plan includes a feature
- `getFeatureLimit(String featureCode)`: Get limit for a specific feature
- `getPlanFeature(String featureCode)`: Get full PlanFeature configuration

#### 4. **SubscriptionAddon** ([SubscriptionAddon.java](src/main/java/com/prosper/prospermentor/entity/SubscriptionAddon.java))
Tracks additional purchases beyond subscription limits.

**Properties:**
- `subscription`: Parent subscription
- `addonType`: Type of addon (e.g., "EXTRA_SESSION")
- `quantity`: Number of units purchased
- `used`: Number of units consumed
- `totalCost`: Total cost paid
- `expiresAt`: Expiration date (optional)
- `status`: AddonStatus enum (ACTIVE, EXHAUSTED, EXPIRED, CANCELLED)

**Methods:**
- `hasRemaining()`: Check if units are available
- `getRemaining()`: Get remaining units
- `consumeUnit()`: Use one unit

## Prosper Mentor Pricing Structure

Based on [ProsperMentorPricing.jpeg](ProsperMentorPricing.jpeg):

### Tier 1: Network (Free - $0)
- **Features:** Network, YouTube
- **Billing:** Free
- **Sessions:** 0

### Tier 2: Learn ($5/month or $50/year)
- **Features:** Network, YouTube, Learn
- **Billing:** Recurring (Monthly/Yearly)
- **Sessions:** 0

### Tier 3: Summit ($8/summit)
- **Features:** Network, YouTube, Learn, Summit
- **Billing:** One-time
- **Sessions:** 0
- **Duration:** 12 months access

### Tier 4: Virtual Mentor ($10/month or $100/year)
- **Features:** Network, YouTube, Learn, Summit, Virtual Mentor
- **Billing:** Recurring (Monthly/Yearly)
- **Sessions:** 0 (unlimited virtual mentor access)

### Tier 5: All Access ($30/month or $300/year)
- **Features:** All features including 1:1 Sessions
- **Billing:** Recurring (Monthly/Yearly)
- **Sessions:** 1 per month
- **Add-ons:** Extra sessions at $20/session

## Service Layer

### SubscriptionService (Enhanced)

#### Feature Access Methods

```java
// Check if user can access a feature
boolean canAccessFeature(UUID userId, String featureCode);

// Get all features for a user with limits
Map<String, Object> getUserFeatures(UUID userId);

// Get specific feature limit
Integer getUserFeatureLimit(UUID userId, String featureCode);
```

**Example Usage:**
```java
// Check if user has access to Learn content
if (subscriptionService.canAccessFeature(userId, "LEARN")) {
    // Show Learn content
}

// Check if user can access Virtual Mentor
if (subscriptionService.canAccessFeature(userId, "VIRTUAL_MENTOR")) {
    // Enable virtual mentor chat
}

// Get all user's features
Map<String, Object> features = subscriptionService.getUserFeatures(userId);
// Returns:
// {
//   "tier": "All Access",
//   "planCode": "ALL_ACCESS",
//   "features": [...],
//   "sessionsRemaining": 1,
//   "addonSessions": 5,
//   "allowsAddons": true,
//   "addonSessionCost": 20.00
// }
```

#### Add-on Management Methods

```java
// Get remaining add-on sessions
int getAddonSessionsRemaining(UUID subscriptionId);

// Purchase add-on sessions
ApiResponse<SubscriptionAddon> purchaseAddonSessions(
    UUID userId,
    int quantity,
    String phoneNumber
);

// Consume session (smart - uses subscription first, then add-ons)
void consumeSessionSmart(UUID userId);

// Get user's add-ons
List<SubscriptionAddon> getUserAddons(UUID userId);
```

**Example Usage:**
```java
// Check if user can book a session (includes add-ons)
if (subscriptionService.canBookSession(userId)) {
    // Allow booking
}

// Purchase 5 extra sessions
ApiResponse<SubscriptionAddon> response =
    subscriptionService.purchaseAddonSessions(userId, 5, "254712345678");

// Consume a session (automatically uses add-ons if subscription exhausted)
subscriptionService.consumeSessionSmart(userId);
```

## Database Schema

### New Tables

1. **features** - Stores available features
2. **plan_features** - Links plans to features with limits
3. **subscription_addons** - Tracks add-on purchases

### Modified Tables

**subscription_plans** - Added columns:
- `billing_type` - Type of billing (RECURRING, ONE_TIME, FREE)
- `yearly_cost` - Yearly pricing option
- `allows_addons` - Whether add-ons are allowed
- `addon_session_cost` - Cost per add-on session

## Migrations

- **V17__Add_feature_system.sql** - Creates tables and adds columns
- **V18__Seed_prosper_mentor_pricing.sql** - Seeds data matching pricing structure

## Benefits of This Architecture

### 1. **Truly Dynamic**
Add new features or tiers without touching code:
```sql
-- Add new feature
INSERT INTO features (code, name, description, type)
VALUES ('PREMIUM_CONTENT', 'Premium Content', 'Exclusive premium resources', 'CONTENT_ACCESS');

-- Add to a plan
INSERT INTO plan_features (plan_id, feature_id, limit_value, enabled)
VALUES ('plan-uuid', 'feature-uuid', -1, true);
```

### 2. **Flexible Pricing Models**
- Free tiers (Network)
- Recurring monthly/yearly (Learn, Virtual Mentor, All Access)
- One-time purchases (Summit)
- Add-on purchases (Extra sessions)

### 3. **Feature-Based Access Control**
```java
// Simple feature checks throughout the application
if (subscriptionService.canAccessFeature(userId, "SUMMIT")) {
    // Show summit recordings
}
```

### 4. **Hierarchical Features**
Plans naturally inherit lower-tier features:
- All Access includes everything from Virtual Mentor
- Virtual Mentor includes everything from Summit
- Summit includes everything from Learn
- Learn includes everything from Network

### 5. **Add-on Support**
Users can purchase extras beyond their plan:
- All Access users can buy extra 1:1 sessions at $20 each
- Add-ons expire with the subscription period
- FIFO consumption (oldest add-ons used first)

### 6. **Better Analytics**
Track feature usage separately:
- Which features are most popular?
- Which plans have highest retention?
- Which add-ons sell best?

## API Endpoints to Create

### Feature Access
```
GET /api/subscriptions/my-features
Response: {
  "tier": "All Access",
  "features": [...],
  "sessionsRemaining": 1,
  "addonSessions": 5
}
```

### Add-on Purchase
```
POST /api/subscriptions/addons/purchase
Body: {
  "quantity": 5,
  "phoneNumber": "254712345678"
}
```

### Feature Check
```
GET /api/subscriptions/can-access/{featureCode}
Response: {
  "hasAccess": true,
  "limit": -1
}
```

## Migration Guide

### For Existing Code

1. **Session Booking** - Update to use `canBookSession()` (already handles add-ons)
2. **Session Consumption** - Replace `consumeSession()` with `consumeSessionSmart()`
3. **Feature Checks** - Replace hardcoded checks with `canAccessFeature()`

Example:
```java
// Old way
if (subscription.getPlan().getCode().equals("ALL_ACCESS")) {
    // Show feature
}

// New way
if (subscriptionService.canAccessFeature(userId, "ONE_ON_ONE")) {
    // Show feature
}
```

## Testing

### Test Scenarios

1. **Free User** - Can access Network and YouTube only
2. **Learn User** - Can access Learn content but not Summit
3. **Summit User** - Can access Summit recordings
4. **Virtual Mentor User** - Can access virtual mentor but not 1:1 sessions
5. **All Access User** - Can access everything + book 1 session/month
6. **All Access with Add-ons** - Can book beyond monthly limit using add-ons
7. **Add-on Expiry** - Add-ons expire at end of billing period

## Future Enhancements

1. **Usage Analytics** - Track which features users actually use
2. **Feature Toggles** - A/B test new features on specific plans
3. **Custom Plans** - Create organization-specific plans
4. **Feature Bundles** - Package features together
5. **Tiered Feature Limits** - Different quality levels (HD vs SD)
6. **Feature Usage Tracking** - Track how many times features are used

## Support

For questions or issues with the feature system:
- Check [SubscriptionService.java](src/main/java/com/prosper/prospermentor/service/SubscriptionService.java) for available methods
- Review [V18__Seed_prosper_mentor_pricing.sql](src/main/resources/db/migration/V18__Seed_prosper_mentor_pricing.sql) for pricing structure
- See entity classes for data models

---

**Implementation Date:** October 2025
**Status:** ✅ Complete and Ready for Testing
