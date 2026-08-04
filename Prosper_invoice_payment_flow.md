# Prospere Invoice Payment Flow

This guide describes how an external system should integrate with ProsperMentor invoices from:
1. Invoice creation
2. Customer payment
3. Payment confirmation

## Base URL and Auth

- Platform API base URL: `https://app.prospermentor.com/api`
- Invoice API base URL (used in examples): `https://app.prospermentor.com/api/v1`
- Protected endpoints require `Authorization: Bearer <token>`
- Public endpoints do not require auth

## Endpoints Used

| Method | Endpoint | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/invoices` | Yes | Create invoice and get hosted payment URL |
| `GET` | `/invoices/public/{publicToken}` | No | Get invoice details |
| `GET` | `/invoices/public/{publicToken}/status` | No | Poll invoice/payment status |
| `POST` | `/invoices/public/{publicToken}/pay` | No | Initiate payment directly (optional advanced flow) |
| `GET` | `/invoices/user/{userId}` | Yes | List invoices for reconciliation |

## Recommended Flow (Hosted Payment Page)

### Step 1: Create invoice

```bash
API_BASE="https://app.prospermentor.com/api/v1"
TOKEN="<bearer-token>"
EXTERNAL_REF="ORD-2026-0001"

curl -X POST "$API_BASE/invoices" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "payerUserId": "1bd479c7-e15e-4400-bdd5-871f3071a48e",
    "amount": 2500,
    "currency": "KES",
    "description": "Mentorship payment - order '"$EXTERNAL_REF"'",
    "metadata": {
      "source": "THIRD_PARTY",
      "externalRef": "'"$EXTERNAL_REF"'"
    },
    "redirectSuccessUrl": "https://thirdparty.example.com/payments/success?externalRef='"$EXTERNAL_REF"'",
    "redirectCancelUrl": "https://thirdparty.example.com/payments/cancel?externalRef='"$EXTERNAL_REF"'"
  }'
```

Example response:

```json
{
  "success": true,
  "message": "Invoice created successfully",
  "data": {
    "invoiceId": "df22e62b-c523-4017-b9ca-88c612d41e08",
    "invoiceNumber": "INV-20260222-166F45A4",
    "publicToken": "d96772fc3c734c15b33fc4e8fd2fc59f",
    "status": "OPEN",
    "amount": 2500,
    "currency": "KES",
    "paymentUrl": "https://<frontend-host>/payment/invoice/d96772fc3c734c15b33fc4e8fd2fc59f",
    "expiresAt": null
  }
}
```

Store at minimum:
- `invoiceId`
- `invoiceNumber`
- `publicToken`
- `metadata.externalRef`

### Step 2: Redirect customer to `paymentUrl`

Your system should redirect the browser to `data.paymentUrl`.

HTTP redirect example:

```http
HTTP/1.1 302 Found
Location: https://<frontend-host>/payment/invoice/d96772fc3c734c15b33fc4e8fd2fc59f
```

The hosted page supports:
- M-Pesa
- Card (CyberSource)

### Step 3: Customer pays

Customer completes payment on hosted checkout.

### Step 4: Customer is redirected back to your system

After checkout:
- Success -> `redirectSuccessUrl`
- Cancel -> `redirectCancelUrl`

These are the URLs you supplied during invoice creation.

### Step 5: Confirm payment server-to-server (required)

Do not trust redirect alone. Always confirm invoice status:

```bash
PUBLIC_TOKEN="d96772fc3c734c15b33fc4e8fd2fc59f"
curl "$API_BASE/invoices/public/$PUBLIC_TOKEN/status"
```

Treat payment as final only if:
- `data.status == "PAID"`

Optional additional check:
- `data.latestPayment.status == "COMPLETED"`

## Optional Advanced Flow (Direct Payment API)

Use this only if your own UI initiates payment directly instead of using hosted checkout UX.

### A) Initiate M-Pesa directly

```bash
PUBLIC_TOKEN="d96772fc3c734c15b33fc4e8fd2fc59f"

curl -X POST "$API_BASE/invoices/public/$PUBLIC_TOKEN/pay" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "MPESA",
    "phoneNumber": "254712345678"
  }'
```

Example response:

```json
{
  "success": true,
  "message": "Payment initiated",
  "data": {
    "method": "MPESA",
    "paymentId": "c483d09d-3e79-4e16-bb3a-b40f8f1e332f",
    "checkoutRequestId": "ws_CO_...",
    "status": "PENDING",
    "invoiceStatus": "OPEN"
  }
}
```

Then poll `/invoices/public/{publicToken}/status` until `PAID` or terminal failure.

### B) Initiate Card directly

```bash
PUBLIC_TOKEN="d96772fc3c734c15b33fc4e8fd2fc59f"

curl -X POST "$API_BASE/invoices/public/$PUBLIC_TOKEN/pay" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "CARD",
    "returnUrl": "https://thirdparty.example.com/payment/card/return",
    "cancelUrl": "https://thirdparty.example.com/payment/card/cancel"
  }'
```

Example response:

```json
{
  "success": true,
  "message": "Payment initiated",
  "data": {
    "method": "CARD",
    "paymentId": "f81d5ca2-38f9-4de9-9a96-16f9ea5ed650",
    "status": "PENDING",
    "invoiceStatus": "OPEN",
    "cybersourceEndpoint": "https://testsecureacceptance.cybersource.com/pay",
    "cybersourceParams": {
      "access_key": "...",
      "profile_id": "...",
      "signed_field_names": "...",
      "signature": "...",
      "transaction_uuid": "...",
      "reference_number": "..."
    },
    "transactionId": "...",
    "referenceNumber": "..."
  }
}
```

For direct card flow, render an HTML form and POST all `cybersourceParams` to `cybersourceEndpoint`.

## Status Model

Invoice statuses:
- `OPEN`: payable
- `PAID`: completed
- `EXPIRED` / `VOID` / `FAILED`: not payable

Common latest payment statuses:
- `PENDING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

## Error Handling

Common responses:
- `400`: invalid request payload (e.g., missing `payerUserId`, invalid UUID, missing method)
- `403`: unauthorized/forbidden for protected endpoints
- `404`: invoice token not found
- `409`: invoice not payable or business rule conflict
- `500`: server error

Recommended retry behavior:
- Retry `5xx` and transient network failures with backoff.
- Do not retry `4xx` until payload/auth is corrected.

## Reconciliation Pattern

Use `metadata.externalRef` as your stable key to map:
- third-party order/invoice
- ProsperMentor invoice/public token
- final payment confirmation

Suggested process:
1. Save invoice creation response in your DB.
2. On success redirect, mark as `PENDING_CONFIRMATION`.
3. Verify using `/invoices/public/{publicToken}/status`.
4. Mark order as paid only after `PAID`.

## Notes

- Currency defaults to `KES` if omitted, but send `"KES"` explicitly for clarity.
- `paymentUrl` host is derived from configured frontend base or redirect URL origin.
- Current invoice APIs are redirect + polling based (no dedicated invoice webhook endpoint in this module).
