# Subscription Module Guide

## Overview
This module implements subscription management with Mpesa payment integration. Before a user books a session, the system checks if they've maxed out their subscription. If they have, they need to pay via Mpesa to book additional sessions.

## Components Created

### 1. Entities

#### Subscription Entity
- **File**: `src/main/java/com/prosper/prospermentor/entity/Subscription.java`
- **Features**:
  - Tracks subscription plans (FREE, BASIC, STANDARD, PREMIUM, UNLIMITED)
  - Monitors sessions used vs. sessions allowed per month
  - Manages billing periods and auto-renewal
  - Business logic for checking availability and consuming sessions

#### Payment Entity
- **File**: `src/main/java/com/prosper/prospermentor/entity/Payment.java`
- **Features**:
  - Tracks Mpesa transactions
  - Stores checkout request IDs, receipt numbers
  - Supports session and subscription payments
  - Handles payment status (PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED)

### 2. Repositories

#### SubscriptionRepository
- **File**: `src/main/java/com/prosper/prospermentor/repository/SubscriptionRepository.java`
- **Key Methods**:
  - `findActiveSubscriptionByUserId()` - Get user's active subscription
  - `findExpiredActiveSubscriptions()` - Find subscriptions needing renewal
  - `hasActiveSubscription()` - Quick check if user has active subscription

#### PaymentRepository
- **File**: `src/main/java/com/prosper/prospermentor/repository/PaymentRepository.java`
- **Key Methods**:
  - `findByCheckoutRequestId()` - Find payment by Mpesa checkout ID
  - `findByMpesaReceiptNumber()` - Find payment by receipt number
  - `findByUserIdOrderByCreatedAtDesc()` - Get user payment history

### 3. Services

#### SubscriptionService
- **File**: `src/main/java/com/prosper/prospermentor/service/SubscriptionService.java`
- **Key Methods**:
  - `canBookSession(UUID userId)` - Check if user can book (has available sessions)
  - `hasMaxedOutSubscription(UUID userId)` - Check if user needs to pay
  - `consumeSession(UUID userId)` - Decrement available sessions after booking
  - `createSubscription()` - Create new subscription
  - `upgradeSubscription()` - Upgrade to higher plan
  - `cancelSubscription()` - Cancel user subscription

#### MpesaService
- **File**: `src/main/java/com/prosper/prospermentor/service/MpesaService.java`
- **Key Methods**:
  - `initiateSTKPush()` - Initiate Mpesa payment (sends STK push to user's phone)
  - `queryTransactionStatus()` - Check payment status
  - `handleCallback()` - Process Mpesa payment callbacks
  - `getAccessToken()` - Get Mpesa API access token

#### SessionBookingService (Updated)
- **File**: `src/main/java/com/prosper/prospermentor/service/SessionBookingService.java`
- **Changes**:
  - Now checks subscription limits before allowing bookings
  - Automatically consumes sessions after successful booking
  - Throws error if user has maxed out subscription

### 4. Controllers

#### SubscriptionController
- **File**: `src/main/java/com/prosper/prospermentor/controller/SubscriptionController.java`
- **Endpoints**:
  - `GET /api/subscriptions/active?userId={uuid}` - Get active subscription
  - `GET /api/subscriptions/user/{userId}` - Get all user subscriptions
  - `GET /api/subscriptions/{subscriptionId}` - Get subscription by ID
  - `POST /api/subscriptions` - Create new subscription
  - `PUT /api/subscriptions/upgrade` - Upgrade subscription
  - `DELETE /api/subscriptions/cancel?userId={uuid}` - Cancel subscription
  - `GET /api/subscriptions/can-book?userId={uuid}` - Check booking availability
  - `GET /api/subscriptions/plans` - Get available plans

#### PaymentController
- **File**: `src/main/java/com/prosper/prospermentor/controller/PaymentController.java`
- **Endpoints**:
  - `POST /api/payments/mpesa/stk-push/session` - Initiate session payment
  - `POST /api/payments/mpesa/stk-push/subscription` - Initiate subscription payment
  - `GET /api/payments/status/{checkoutRequestId}` - Query payment status
  - `GET /api/payments/{paymentId}` - Get payment details
  - `GET /api/payments/user/{userId}` - Get user payment history
  - `POST /api/payments/mpesa/callback` - Mpesa callback webhook
  - `PUT /api/payments/{paymentId}/cancel` - Cancel payment
  - `GET /api/payments/session/{sessionId}` - Get session payments

### 5. Database Migration
- **File**: `src/main/resources/db/migration/V6__Create_subscriptions_and_payments_tables.sql`
- **Creates**:
  - `subscriptions` table with indexes
  - `payments` table with indexes
  - Triggers for auto-updating timestamps

## How It Works

### Booking Flow with Subscription Check

1. **User attempts to book a session** → Calls `POST /api/sessions/request`

2. **SessionBookingService checks subscription**:
   ```java
   boolean canBook = subscriptionService.canBookSession(menteeId);
   if (!canBook) {
       throw new IllegalStateException("Subscription limit reached.
                                        Please upgrade your plan or make a payment.");
   }
   ```

3. **If user has available sessions**:
   - Session is created
   - Session is consumed from subscription
   - Notification sent to mentor

4. **If user has maxed out subscription**:
   - Error is returned
   - User must upgrade subscription or pay for individual session

### Payment Flow

1. **User initiates payment** → Calls `POST /api/payments/mpesa/stk-push/session`

2. **MpesaService processes request**:
   - Creates payment record in database
   - Generates Mpesa password and timestamp
   - Calls Mpesa API to initiate STK Push

3. **User receives STK Push on phone**:
   - Enters PIN to complete payment
   - Mpesa processes transaction

4. **Mpesa sends callback** → `POST /api/payments/mpesa/callback`
   - Payment status updated in database
   - If successful, user can now book session

5. **Frontend polls for status** → `GET /api/payments/status/{checkoutRequestId}`
   - Checks if payment completed
   - Updates UI accordingly

## Configuration

Add these to your `application.properties` or environment variables:

```properties
# Mpesa Configuration (Sandbox)
mpesa.consumer.key=your_consumer_key
mpesa.consumer.secret=your_consumer_secret
mpesa.api.url=https://sandbox.safaricom.co.ke
mpesa.shortcode=174379
mpesa.passkey=your_passkey
mpesa.callback.url=https://yourdomain.com/api/payments/mpesa/callback

# Production
# mpesa.api.url=https://api.safaricom.co.ke
```

## Subscription Plans

| Plan | Sessions/Month | Features |
|------|---------------|----------|
| FREE | 2 | Basic access |
| BASIC | 5 | More sessions |
| STANDARD | 10 | Standard access |
| PREMIUM | 20 | Premium access |
| UNLIMITED | ∞ | Unlimited sessions |

## Testing Mpesa Integration

### Sandbox Test Credentials
- **Shortcode**: 174379
- **Test Phone Numbers**: Any Safaricom number
- **Test PIN**: Any 4 digits

### Test Flow
1. Create a user subscription
2. Try to book sessions until limit is reached
3. Initiate STK Push payment
4. Use test PIN on phone to complete
5. Verify payment status updates
6. Confirm user can now book sessions

## API Examples

### Check if user can book
```bash
curl -X GET "http://localhost:8080/api/subscriptions/can-book?userId={uuid}"
```

Response:
```json
{
  "success": true,
  "canBook": true,
  "remainingSessions": 3,
  "message": "User has 3 sessions remaining"
}
```

### Initiate session payment
```bash
curl -X POST "http://localhost:8080/api/payments/mpesa/stk-push/session" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-uuid",
    "sessionId": "session-uuid",
    "amount": 500,
    "phoneNumber": "254712345678",
    "description": "Session payment"
  }'
```

Response:
```json
{
  "success": true,
  "message": "STK Push initiated successfully. Please check your phone.",
  "paymentId": "payment-uuid",
  "checkoutRequestId": "ws_CO_xxx",
  "amount": 500.00,
  "phoneNumber": "254712345678"
}
```

### Query payment status
```bash
curl -X GET "http://localhost:8080/api/payments/status/{checkoutRequestId}"
```

Response:
```json
{
  "success": true,
  "payment": {
    "id": "payment-uuid",
    "status": "COMPLETED",
    "amount": 500.00,
    "mpesaReceiptNumber": "QGR7XYZ123",
    "transactionDate": "2025-09-30T10:30:00"
  },
  "status": "COMPLETED",
  "isCompleted": true
}
```

## Troubleshooting

### Issue: Flyway validation error
**Solution**: Already handled. Validation is temporarily disabled on first run to allow migration.

### Issue: Circular dependency error
**Solution**: Already fixed by moving `@EnableJpaAuditing` to `DatabaseConfig` class.

### Issue: Mpesa STK Push not received
**Check**:
- Phone number format (254XXXXXXXXX)
- Mpesa credentials are correct
- Phone has airtime/active Safaricom line
- Using sandbox environment with test credentials

### Issue: Payment callback not working
**Check**:
- Callback URL is publicly accessible (use ngrok for local testing)
- Callback endpoint is not behind authentication
- Database connection is active

## Security Notes

1. **Never commit Mpesa credentials** - Use environment variables
2. **Validate callback authenticity** - Check request origin
3. **Secure webhook endpoint** - Consider IP whitelisting for Safaricom IPs
4. **Encrypt sensitive data** - Store payment details securely
5. **Implement rate limiting** - Prevent abuse of payment endpoints

## Next Steps

1. **Add webhook authentication** - Validate Mpesa callbacks
2. **Implement payment notifications** - Email/SMS on successful payment
3. **Add payment reconciliation** - Daily checks against Mpesa reports
4. **Create admin dashboard** - Monitor subscriptions and payments
5. **Add payment analytics** - Track revenue and conversion rates

## Support

For issues or questions:
- Check logs in `/logs/` directory
- Review Mpesa documentation: https://developer.safaricom.co.ke
- Contact support team