# JWT Bearer Token Demo

## What You Get from Supabase Login

When you call your `/api/auth/login` endpoint, here's exactly what happens:

### 1. Your App → Supabase
```http
POST https://moadpjwbdicdficwevjj.supabase.co/auth/v1/token?grant_type=password
Content-Type: application/json
apikey: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

{
  "email": "user@example.com",
  "password": "password123"
}
```

### 2. Supabase → Your App
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzI1Nzk2Mjk4LCJpYXQiOjE3MjU3OTI2OTgsImlzcyI6Imh0dHBzOi8vbW9hZHBqd2JkaWNkZmljd2V2amoucnVwYWJhc2UuY28vYXV0aC92MSIsInN1YiI6IjEyMzQ1Njc4LTkwYWItY2RlZi0xMjM0LTU2Nzg5MGFiY2RlZiIsImVtYWlsIjoidXNlckBleGFtcGxlLmNvbSIsInVzZXJfbWV0YWRhdGEiOnsicm9sZSI6Im1lbnRlZSJ9fQ.signature_here",
  "token_type": "bearer",
  "expires_in": 3600,
  "expires_at": 1725796298,
  "refresh_token": "refresh_token_here",
  "user": {
    "id": "12345678-90ab-cdef-1234-567890abcdef",
    "aud": "authenticated",
    "role": "authenticated",
    "email": "user@example.com",
    "email_confirmed_at": "2024-01-01T00:00:00Z",
    "user_metadata": {
      "role": "mentee"
    }
  }
}
```

### 3. Client → Your App (Using Bearer Token)
```http
GET /api/profiles/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzI1Nzk2Mjk4...
```

## JWT Token Structure

If you decode the JWT token (using jwt.io), you'll see:

### Header:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

### Payload:
```json
{
  "aud": "authenticated",
  "exp": 1725796298,
  "iat": 1725792698,
  "iss": "https://moadpjwbdicdficwevjj.supabase.co/auth/v1",
  "sub": "12345678-90ab-cdef-1234-567890abcdef",
  "email": "user@example.com",
  "phone": "",
  "app_metadata": {
    "provider": "email",
    "providers": ["email"]
  },
  "user_metadata": {
    "role": "mentee"
  }
}
```

### Signature:
Signed with your Supabase JWT secret

## How Your Spring Boot App Uses It

### JwtAuthenticationFilter Flow:
1. **Extract**: `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
2. **Validate**: Check signature using Supabase JWT secret
3. **Extract Claims**: 
   - `sub` → userId
   - `email` → email  
   - `user_metadata.role` → role
4. **Create Authentication**: Spring Security context
5. **Allow Access**: To protected endpoints

## Why This Works

✅ **Same Secret**: Your app uses the same JWT secret as Supabase  
✅ **Standard JWT**: Supabase uses industry-standard JWT format  
✅ **Bearer Token**: JWT is designed to be used as bearer tokens  
✅ **Stateless**: No need to store sessions, everything is in the token  

## Security Benefits

1. **No Database Lookups**: Token contains all needed info
2. **Tamper Proof**: Signature ensures token hasn't been modified
3. **Expiration**: Tokens automatically expire
4. **Refresh**: Use refresh tokens to get new access tokens
5. **Role-Based**: Role information is embedded in the token

## Frontend Usage Example

```javascript
// 1. Login and get token
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});

const { access_token } = await loginResponse.json();

// 2. Store token
localStorage.setItem('token', access_token);

// 3. Use token for ALL protected requests
const token = localStorage.getItem('token');

const profileResponse = await fetch('/api/profiles/me', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const mentorResponse = await fetch('/api/demo/mentor-only', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

## The Answer to Your Question

**YES**, you can and should use the Supabase access token directly as a bearer token because:

1. It's a standard JWT token
2. Your Spring Boot app validates it using the same secret
3. It contains all necessary user information
4. It's designed specifically for this purpose
5. This is the industry-standard approach

The `access_token` from Supabase **IS** your bearer token! 🎯
