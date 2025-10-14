# Subscription Upgrade with Proration

## Overview

The subscription upgrade feature now includes automatic proration, ensuring users only pay the difference when upgrading to a higher-tier plan.

## How Proration Works

### Calculation Formula

When a user upgrades their subscription, the system calculates a prorated amount based on:

1. **Days Remaining**: Number of days left in the current billing period
2. **Total Days**: Total days in the current billing period
3. **Unused Ratio**: `daysRemaining / totalDays`

**Prorated Amount** = (New Plan Cost × Unused Ratio) - (Current Plan Cost × Unused Ratio)

### Example

**Scenario:**
- User is on **Basic Plan** ($50/month)
- User has used 10 days of a 30-day period (20 days remaining)
- User wants to upgrade to **Premium Plan** ($100/month)

**Calculation:**
```
Unused Ratio = 20 / 30 = 0.6667

Unused Current Plan Value = $50 × 0.6667 = $33.33
New Plan Prorated Cost = $100 × 0.6667 = $66.67

Prorated Amount = $66.67 - $33.33 = $33.34
```

The user pays **$33.34** to upgrade immediately, and the subscription period remains unchanged.

## API Changes

### 1. Updated Request DTO

**UpgradeSubscriptionRequest** now requires a phone number for payment:

```json
{
  "userId": "uuid",
  "newPlanId": "uuid",
  "phoneNumber": "254XXXXXXXXX"
}
```

### 2. Updated Response DTO

**SubscriptionUpgradeResponse** includes payment and proration details:

```json
{
  "success": true,
  "message": "Payment initiated for upgrade. Please complete payment on your phone.",
  "data": {
    "subscription": { ... },
    "payment": {
      "paymentId": "uuid",
      "checkoutRequestId": "ws_CO_xxx",
      "amount": "33.34",
      "currency": "KES",
      "phoneNumber": "254XXXXXXXXX",
      "status": "PENDING",
      "description": "Upgrade to Premium plan (prorated)"
    },
    "proratedAmount": 33.34,
    "newPlan": { ... },
    "message": "Payment initiated. Subscription will be upgraded after payment confirmation."
  }
}
```

## Payment Flow

### 1. Upgrade Initiation

When a user requests an upgrade:

1. System validates the upgrade (new plan must have higher cost)
2. Calculates prorated amount
3. Initiates M-Pesa STK push with prorated amount
4. Stores target plan ID in payment metadata
5. Returns payment details to user

### 2. Payment Processing

User completes payment on their phone via M-Pesa STK push.

### 3. Callback Handling

When M-Pesa sends a callback:

**Success:**
1. Payment status updated to `COMPLETED`
2. Target plan ID extracted from payment metadata
3. `completeSubscriptionUpgrade()` called
4. Subscription updated to new plan
5. Sessions reset to new plan's allocation
6. User immediately has access to new plan features

**Failure:**
1. Payment status updated to `FAILED`
2. Subscription remains on current plan
3. No charges applied

## Database Changes

### Payment Entity

Added new `PaymentType`:
```java
public enum PaymentType {
    SESSION_BOOKING,
    SUBSCRIPTION,
    UPGRADE,          // New type for upgrades
    ADDON,
    TOP_UP,
    REFUND
}
```

### Metadata Storage

Payment metadata stores the target plan ID in JSON format:
```json
{"targetPlanId":"uuid-here"}
```

## Service Methods

### SubscriptionService

1. **upgradeSubscription(request)**
   - Validates upgrade eligibility
   - Calculates prorated amount
   - Initiates payment
   - Returns upgrade response with payment details

2. **calculateProratedUpgradeAmount(subscription, currentPlan, newPlan)**
   - Private helper method
   - Performs proration calculation
   - Logs calculation details for debugging

3. **completeSubscriptionUpgrade(subscriptionId, newPlanId)**
   - Called by payment callback
   - Updates subscription to new plan
   - Resets session count
   - Activates new plan immediately

### MpesaService

1. **handleUpgradePaymentSuccess(payment)**
   - Extracts target plan ID from metadata
   - Calls `completeSubscriptionUpgrade()`
   - Logs upgrade completion

2. **handleUpgradePaymentFailure(payment)**
   - Logs failure
   - No action needed (subscription stays on current plan)

3. **extractTargetPlanIdFromMetadata(metadata)**
   - Parses JSON metadata
   - Extracts UUID
   - Returns target plan ID

## Key Features

✅ **Fair Pricing**: Users only pay for the remaining period difference
✅ **Immediate Upgrade**: Access to new features starts immediately after payment
✅ **Safe Rollback**: Failed payments don't affect current subscription
✅ **Period Preservation**: Billing cycle remains unchanged
✅ **Session Reset**: Users get full session allocation of new plan

## Error Handling

1. **Invalid Upgrade**: Rejects downgrades or same-plan changes
2. **Payment Failure**: Subscription remains on current plan
3. **Missing Metadata**: Logs error, upgrade incomplete
4. **Invalid Plan ID**: Logs error, returns error response

## Testing Recommendations

1. **Test Different Time Points**:
   - Beginning of period (high prorated cost)
   - Middle of period (medium prorated cost)
   - End of period (low prorated cost)

2. **Test Payment Scenarios**:
   - Successful payment
   - Failed payment
   - Cancelled payment
   - Timeout scenarios

3. **Test Edge Cases**:
   - Upgrade on last day of period
   - Upgrade with 0 days remaining
   - Multiple rapid upgrade attempts

## Security Considerations

- Phone number validation required
- Payment confirmation via M-Pesa callback
- Metadata stored in secure JSON format
- All monetary calculations use BigDecimal with 2 decimal precision
- Failed upgrades don't affect existing subscription
