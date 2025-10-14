# New API Endpoints - Feature-Based Subscription System

This document lists all the new API endpoints added to support the dynamic feature-based subscription system.

## Feature Access Endpoints

### 1. Get User Features
**GET** `/api/v1/subscriptions/features?userId={userId}`

Get all features available to a user with their subscription details.

**Response:**
```json
{
  "success": true,
  "data": {
    "tier": "All Access",
    "planCode": "ALL_ACCESS",
    "features": [
      {
        "code": "NETWORK",
        "name": "Prosper Mentor Network",
        "description": "Community of mentors and mentees",
        "type": "COMMUNITY",
        "limit": -1,
        "unlimited": true
      },
      {
        "code": "ONE_ON_ONE",
        "name": "Prosper Mentor 1:1 Sessions",
        "description": "Live mentorship sessions",
        "type": "MENTOR_SESSION",
        "limit": 1,
        "unlimited": false
      }
    ],
    "sessionsRemaining": 1,
    "addonSessions": 5,
    "allowsAddons": true,
    "addonSessionCost": 20.00
  }
}
```

**Use Cases:**
- Display user's tier and available features on dashboard
- Show what features unlock with each subscription tier
- Display remaining sessions and add-on availability

---

### 2. Check Feature Access
**GET** `/api/v1/subscriptions/features/{featureCode}/check?userId={userId}`

Check if a user has access to a specific feature.

**Parameters:**
- `featureCode`: Feature code (e.g., "LEARN", "SUMMIT", "VIRTUAL_MENTOR", "ONE_ON_ONE")
- `userId`: User's UUID

**Response (Has Access):**
```json
{
  "success": true,
  "hasAccess": true,
  "featureCode": "LEARN",
  "limit": -1,
  "unlimited": true
}
```

**Response (No Access):**
```json
{
  "success": true,
  "hasAccess": false,
  "featureCode": "SUMMIT",
  "limit": 0,
  "unlimited": false,
  "message": "Upgrade your subscription to access this feature"
}
```

**Use Cases:**
- Gate content based on subscription tier
- Show/hide UI elements based on feature access
- Display upgrade prompts when users try to access restricted features

**Feature Codes:**
- `NETWORK` - Prosper Mentor Network (Community)
- `YOUTUBE` - YouTube Channel Access
- `LEARN` - Curated Content Library
- `SUMMIT` - Summit Recordings
- `VIRTUAL_MENTOR` - 24/7 Virtual Mentor
- `ONE_ON_ONE` - 1:1 Mentor Sessions

---

## Add-on Endpoints

### 3. Get User Add-ons
**GET** `/api/v1/subscriptions/addons?userId={userId}`

Get all add-ons purchased by the user.

**Response:**
```json
{
  "success": true,
  "addons": [
    {
      "id": "uuid",
      "addonType": "EXTRA_SESSION",
      "addonName": "Extra 1:1 Sessions",
      "quantity": 5,
      "used": 2,
      "totalCost": 100.00,
      "currency": "USD",
      "purchasedAt": "2025-10-01T10:00:00",
      "expiresAt": "2025-11-01T10:00:00",
      "status": "ACTIVE"
    }
  ],
  "totalRemaining": 3,
  "count": 1
}
```

**Use Cases:**
- Display purchased add-ons on user profile
- Show remaining add-on sessions
- Track add-on purchase history

---

### 4. Purchase Add-on Sessions
**POST** `/api/v1/subscriptions/addons/purchase`

Purchase extra 1:1 sessions beyond subscription limit (All Access tier only).

**Parameters:**
- `userId`: User's UUID
- `quantity`: Number of sessions to purchase (1-50)
- `phoneNumber`: M-Pesa phone number for payment

**Request:**
```
POST /api/v1/subscriptions/addons/purchase?userId={userId}&quantity=5&phoneNumber=254712345678
```

**Response (Success):**
```json
{
  "success": true,
  "message": "Payment initiated for add-on sessions",
  "data": {
    "id": "uuid",
    "addonType": "EXTRA_SESSION",
    "quantity": 5,
    "totalCost": 100.00,
    "status": "ACTIVE",
    "paymentId": "payment-uuid"
  }
}
```

**Response (Not All Access):**
```json
{
  "success": false,
  "message": "Add-on sessions are only available for All Access members"
}
```

**Use Cases:**
- Allow All Access users to buy extra sessions when they run out
- Initiate M-Pesa STK push for payment
- Track add-on purchases

---

### 5. Get Remaining Add-on Sessions
**GET** `/api/v1/subscriptions/addons/remaining?userId={userId}`

Get count of remaining add-on sessions for a user.

**Response:**
```json
{
  "success": true,
  "remainingAddons": 5
}
```

**Use Cases:**
- Display remaining add-on sessions in UI
- Show when user should purchase more add-ons
- Calculate total available sessions (subscription + add-ons)

---

## Updated Existing Endpoints

### 6. Can Book Session (Enhanced)
**GET** `/api/v1/subscriptions/can-book?userId={userId}`

Now checks both subscription sessions AND add-on sessions.

**Response:**
```json
{
  "success": true,
  "canBook": true,
  "remainingSessions": 0,
  "message": "Using add-on sessions"
}
```

**Changes:**
- Now includes add-on sessions in availability check
- Returns `true` even if subscription exhausted but add-ons available
- Smart session consumption (uses subscription first, then add-ons)

---

## Frontend Integration Examples

### Check Feature Access Before Showing Content

```javascript
// Check if user can access Learn content
const checkLearnAccess = async (userId) => {
  const response = await fetch(
    `/api/v1/subscriptions/features/LEARN/check?userId=${userId}`
  );
  const data = await response.json();

  if (data.hasAccess) {
    // Show Learn content
    showLearnContent();
  } else {
    // Show upgrade prompt
    showUpgradePrompt('Learn', '$5/month');
  }
};
```

### Display User's Features on Dashboard

```javascript
// Get all user features
const loadUserFeatures = async (userId) => {
  const response = await fetch(
    `/api/v1/subscriptions/features?userId=${userId}`
  );
  const data = await response.json();

  // Display tier
  console.log(`Tier: ${data.data.tier}`);

  // Show available features
  data.data.features.forEach(feature => {
    console.log(`✓ ${feature.name}`);
  });

  // Show sessions
  console.log(`Sessions remaining: ${data.data.sessionsRemaining}`);
  console.log(`Add-on sessions: ${data.data.addonSessions}`);

  // Show add-on purchase option if applicable
  if (data.data.allowsAddons) {
    showAddonPurchaseButton(data.data.addonSessionCost);
  }
};
```

### Purchase Add-on Sessions

```javascript
// Purchase 5 extra sessions
const purchaseAddons = async (userId, quantity, phoneNumber) => {
  const response = await fetch(
    `/api/v1/subscriptions/addons/purchase?userId=${userId}&quantity=${quantity}&phoneNumber=${phoneNumber}`,
    { method: 'POST' }
  );
  const data = await response.json();

  if (data.success) {
    alert('Payment initiated! Please complete on your phone.');
  } else {
    alert(data.message);
  }
};
```

### Display Remaining Sessions (Including Add-ons)

```javascript
// Show total available sessions
const displayAvailableSessions = async (userId) => {
  // Get subscription sessions
  const subResponse = await fetch(
    `/api/v1/subscriptions/active?userId=${userId}`
  );
  const subData = await subResponse.json();
  const subSessions = subData.remainingSessions || 0;

  // Get add-on sessions
  const addonResponse = await fetch(
    `/api/v1/subscriptions/addons/remaining?userId=${userId}`
  );
  const addonData = await addonResponse.json();
  const addonSessions = addonData.remainingAddons || 0;

  // Display total
  console.log(`Subscription: ${subSessions} sessions`);
  console.log(`Add-ons: ${addonSessions} sessions`);
  console.log(`Total: ${subSessions + addonSessions} sessions`);
};
```

---

## Testing the Endpoints

### Using cURL

```bash
# Get user features
curl "http://localhost:8080/api/v1/subscriptions/features?userId=user-uuid"

# Check specific feature access
curl "http://localhost:8080/api/v1/subscriptions/features/LEARN/check?userId=user-uuid"

# Get user add-ons
curl "http://localhost:8080/api/v1/subscriptions/addons?userId=user-uuid"

# Purchase add-on sessions
curl -X POST "http://localhost:8080/api/v1/subscriptions/addons/purchase?userId=user-uuid&quantity=5&phoneNumber=254712345678"

# Get remaining add-on sessions
curl "http://localhost:8080/api/v1/subscriptions/addons/remaining?userId=user-uuid"
```

### Using Postman

Import the following collection to test all endpoints:

1. Create a new Postman collection
2. Add the endpoints above
3. Set `{{baseUrl}}` = `http://localhost:8080`
4. Set `{{userId}}` = your test user's UUID

---

## Migration from Old Approach

### Before (Hardcoded Plan Checks)
```java
// Controller
if (subscription.getPlan().getCode().equals("ALL_ACCESS")) {
    // Show feature
}
```

### After (Dynamic Feature Checks)
```java
// Controller
if (subscriptionService.canAccessFeature(userId, "ONE_ON_ONE")) {
    // Show feature
}

// Or use the endpoint
GET /api/v1/subscriptions/features/ONE_ON_ONE/check?userId={userId}
```

---

## Summary of Changes

### New Endpoints (5)
1. `GET /api/v1/subscriptions/features` - Get all user features
2. `GET /api/v1/subscriptions/features/{featureCode}/check` - Check feature access
3. `GET /api/v1/subscriptions/addons` - Get user add-ons
4. `POST /api/v1/subscriptions/addons/purchase` - Purchase add-on sessions
5. `GET /api/v1/subscriptions/addons/remaining` - Get remaining add-ons

### Enhanced Endpoints (1)
1. `GET /api/v1/subscriptions/can-book` - Now includes add-on sessions

### Feature Codes (6)
- `NETWORK`, `YOUTUBE`, `LEARN`, `SUMMIT`, `VIRTUAL_MENTOR`, `ONE_ON_ONE`

### Subscription Tiers (5)
- Network (Free), Learn ($5/mo), Summit ($8), Virtual Mentor ($10/mo), All Access ($30/mo)

---

## Next Steps

1. **Test the endpoints** - Run migrations and test each endpoint
2. **Update frontend** - Integrate feature checks into your UI
3. **Add security** - Ensure proper authentication on all endpoints
4. **Monitor usage** - Track which features are most popular
5. **Analytics** - Build dashboards showing tier distribution and feature usage

---

For complete documentation, see:
- [DYNAMIC_SUBSCRIPTION_SYSTEM.md](DYNAMIC_SUBSCRIPTION_SYSTEM.md) - Full system architecture
- [FEATURE_ACCESS_QUICK_REFERENCE.md](FEATURE_ACCESS_QUICK_REFERENCE.md) - Code examples
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Implementation details
