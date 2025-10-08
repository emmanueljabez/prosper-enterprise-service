# Supabase Username & Password Authentication Setup

This document explains how to use the implemented username/password authentication with Supabase.

## Overview

The application now supports:
- User registration with email/password
- User login with email/password  
- JWT token refresh
- User logout
- Secure token validation

## API Endpoints

### 1. Sign Up
```http
POST /api/auth/signup
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "yourpassword",
  "role": "mentee"  // Optional: "mentee" (default) or "mentor"
}
```

**Response (Success):**
```json
{
  "access_token": "eyJ...",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "...",
  "user": {
    "id": "...",
    "email": "user@example.com",
    "user_metadata": {
      "role": "mentee"
    }
  }
}
```

### 2. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "yourpassword"
}
```

**Response (Success):** Same as signup response

### 3. Refresh Token
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "your-refresh-token"
}
```

### 4. Logout
```http
POST /api/auth/logout
Authorization: Bearer your-access-token
```

### 5. Get Profile (Protected)
```http
GET /api/auth/profile
Authorization: Bearer your-access-token
```

### 6. Get Complete Profile from Database (Protected)
```http
GET /api/auth/profile/complete
Authorization: Bearer your-access-token
```

### 7. Get Basic Profile from Database (Protected)
```http
GET /api/auth/profile/basic
Authorization: Bearer your-access-token
```

### 8. Update Profile (Protected)
```http
PUT /api/auth/profile/update
Authorization: Bearer your-access-token
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "bio": "Updated bio",
  "location": "New York, NY",
  "industry": "Technology"
}
```

## Configuration Required

### 1. JWT Secret
⚠️ **IMPORTANT**: Update the JWT secret in `application.properties`:

```properties
supabase.jwt-secret=your-actual-jwt-secret-from-supabase
```

To get your JWT secret:
1. Go to your Supabase Dashboard
2. Navigate to Settings → API
3. Copy the "JWT Secret" value
4. Replace `your-jwt-secret` in application.properties

### 2. Email Confirmation (Optional)
By default, Supabase requires email confirmation. To disable this for development:
1. Go to Supabase Dashboard → Authentication → Settings
2. Disable "Enable email confirmations"

## Security Features

- ✅ Password validation (minimum 6 characters)
- ✅ Email format validation
- ✅ JWT token validation
- ✅ Role-based authorization
- ✅ Secure error handling
- ✅ CORS configuration

## Usage Example

### Frontend JavaScript
```javascript
// Sign up
const signUp = async (email, password, role = 'mentee') => {
  const response = await fetch('/api/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, role })
  });
  return response.json();
};

// Login
const login = async (email, password) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  return response.json();
};

// Use token for authenticated requests
const getProfile = async (accessToken) => {
  const response = await fetch('/api/auth/profile', {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  return response.json();
};
```

## Profile Management Endpoints

The application also provides dedicated profile endpoints at `/api/profiles`:

### Get My Complete Profile
```http
GET /api/profiles/me
Authorization: Bearer your-access-token
```
**Response:** Complete profile with role-specific data (mentee/mentor profile if exists)

### Get My Basic Profile
```http
GET /api/profiles/me/basic
Authorization: Bearer your-access-token
```
**Response:** Basic profile information from profiles table

### Get My Mentee Profile (Mentees only)
```http
GET /api/profiles/me/mentee
Authorization: Bearer your-access-token
```
**Response:** Mentee-specific profile data (goals, learning style, etc.)

### Get My Mentor Profile (Mentors only)
```http
GET /api/profiles/me/mentor
Authorization: Bearer your-access-token
```
**Response:** Mentor-specific profile data (rates, specializations, etc.)

### Update My Profile
```http
PUT /api/profiles/me
Authorization: Bearer your-access-token
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "bio": "Software engineer with 5 years of experience",
  "location": "San Francisco, CA",
  "industry": "Technology",
  "linkedinUrl": "https://linkedin.com/in/johndoe",
  "phone": "+1-555-0123"
}
```

### Check if My Profile Exists
```http
GET /api/profiles/me/exists
Authorization: Bearer your-access-token
```
**Response:** `{"exists": true, "userId": "user-uuid"}`

## Testing

You can test the authentication using curl:

```bash
# Sign up
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"testpass123","role":"mentee"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"testpass123"}'

# Get profile (use token from login response)
curl -X GET http://localhost:8080/api/auth/profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# Get complete profile from database
curl -X GET http://localhost:8080/api/profiles/me \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# Check if profile exists in database
curl -X GET http://localhost:8080/api/profiles/me/exists \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# Update profile information
curl -X PUT http://localhost:8080/api/profiles/me \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -d '{"firstName":"John","lastName":"Doe","bio":"Updated bio"}'
```

## Next Steps

1. Update the JWT secret in application.properties
2. Configure email settings in Supabase if needed
3. Test the authentication flow
4. Implement frontend integration
5. Add password reset functionality (if needed)
