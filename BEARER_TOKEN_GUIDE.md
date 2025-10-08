# Supabase JWT as Bearer Token in Spring Boot

This guide explains how Supabase JWT tokens work seamlessly as bearer tokens for protecting URLs in Spring Boot.

## Overview

✅ **Already Implemented!** Your application already uses Supabase JWT tokens as bearer tokens for Spring Security.

## How It Works

### 1. **User Authentication**
```bash
# User logs in
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzI1Nzk2Mjk4LCJpYXQiOjE3MjU3OTI2OTgsImlzcyI6Imh0dHBzOi8vbW9hZHBqd2JkaWNkZmljd2V2amoucnVwYWJhc2UuY28vYXV0aC92MSIsInN1YiI6IjEyMzQ1Njc4LTkwYWItY2RlZi0xMjM0LTU2Nzg5MGFiY2RlZiIsImVtYWlsIjoidXNlckBleGFtcGxlLmNvbSIsInBob25lIjoiIiwiYXBwX21ldGFkYXRhIjp7InByb3ZpZGVyIjoiZW1haWwiLCJwcm92aWRlcnMiOlsiZW1haWwiXX0sInVzZXJfbWV0YWRhdGEiOnsicm9sZSI6Im1lbnRlZSJ9fQ.example-signature",
  "token_type": "bearer",
  "expires_in": 3600,
  "refresh_token": "refresh-token-here",
  "user": {
    "id": "12345678-90ab-cdef-1234-567890abcdef",
    "email": "user@example.com",
    "user_metadata": {
      "role": "mentee"
    }
  }
}
```

### 2. **Using Bearer Token for Protected URLs**

The `access_token` from the response is your bearer token. Use it in the `Authorization` header:

```bash
# Store the token
export TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhdXRoZW50aWNhdGVkIi..."

# Access protected endpoints
curl -X GET http://localhost:8080/api/profiles/me \
  -H "Authorization: Bearer $TOKEN"
```

## Protected URL Examples

### **Basic Protected Endpoint**
```bash
# Get user profile
curl -X GET http://localhost:8080/api/auth/profile \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "userId": "12345678-90ab-cdef-1234-567890abcdef",
  "email": "user@example.com",
  "role": "MENTEE",
  "displayName": "John Doe",
  "hasCompleteProfile": true,
  "authenticated": true
}
```

### **Role-Based Protected Endpoint**
```bash
# Only mentors can access this (will return 403 for non-mentors)
curl -X GET http://localhost:8080/api/demo/mentor-only \
  -H "Authorization: Bearer $TOKEN"
```

### **Complete Profile Data**
```bash
# Get complete profile from database
curl -X GET http://localhost:8080/api/profiles/me \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "id": "12345678-90ab-cdef-1234-567890abcdef",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "role": "MENTEE",
  "bio": "Software engineer looking for career guidance",
  "location": "San Francisco, CA",
  "menteeProfile": {
    "goals": ["Career advancement", "Technical skills"],
    "learningStyle": "Visual",
    "budgetRange": "$50-100/hour"
  }
}
```

## Spring Security Configuration

### **Current Security Rules**
Your `SecurityConfig` already defines these protection levels:

```java
// Public endpoints (no token needed)
.requestMatchers("/api/auth/login", "/api/auth/signup", "/api/auth/refresh").permitAll()
.requestMatchers("/api/demo/public").permitAll()

// Role-based protection
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/mentor/**").hasAnyRole("ADMIN", "MENTOR")

// All other requests require authentication (any valid bearer token)
.anyRequest().authenticated()
```

### **How JWT Validation Works**

1. **Request arrives** with `Authorization: Bearer <token>`
2. **JwtAuthenticationFilter** extracts the token
3. **JwtUtil** validates the token using Supabase JWT secret
4. **SupabaseUserDetailsService** loads user details (from database if available)
5. **Spring Security context** is set with authenticated user
6. **Request proceeds** to the controller

## Method-Level Security

You can also use method-level security with `@PreAuthorize`:

```java
@GetMapping("/mentor-dashboard")
@PreAuthorize("hasRole('MENTOR')")
public ResponseEntity<?> mentorDashboard(Authentication auth) {
    SupabaseUserDetails user = (SupabaseUserDetails) auth.getPrincipal();
    return ResponseEntity.ok("Welcome " + user.getDisplayName());
}

@GetMapping("/admin-panel")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminPanel() {
    return ResponseEntity.ok("Admin access granted");
}

@GetMapping("/mentor-or-admin")
@PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
public ResponseEntity<?> mentorOrAdmin() {
    return ResponseEntity.ok("Mentor or admin access");
}
```

## Testing Different Scenarios

### **1. No Token (Should return 401)**
```bash
curl -X GET http://localhost:8080/api/profiles/me
# Returns: 401 Unauthorized
```

### **2. Invalid Token (Should return 401)**
```bash
curl -X GET http://localhost:8080/api/profiles/me \
  -H "Authorization: Bearer invalid-token"
# Returns: 401 Unauthorized
```

### **3. Valid Token but Wrong Role (Should return 403)**
```bash
# If user is MENTEE trying to access mentor-only endpoint
curl -X GET http://localhost:8080/api/demo/mentor-only \
  -H "Authorization: Bearer $MENTEE_TOKEN"
# Returns: 403 Forbidden
```

### **4. Valid Token with Correct Role (Should return 200)**
```bash
# Mentor accessing mentor endpoint
curl -X GET http://localhost:8080/api/demo/mentor-only \
  -H "Authorization: Bearer $MENTOR_TOKEN"
# Returns: 200 OK with data
```

## Frontend Integration

### **JavaScript Example**
```javascript
// Store token after login
const loginResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});

const { access_token } = await loginResponse.json();
localStorage.setItem('token', access_token);

// Use token for protected requests
const token = localStorage.getItem('token');

const profileResponse = await fetch('/api/profiles/me', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const profile = await profileResponse.json();
```

### **React Hook Example**
```javascript
const useAuth = () => {
  const [token, setToken] = useState(localStorage.getItem('token'));
  
  const apiCall = async (url, options = {}) => {
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        'Authorization': `Bearer ${token}`
      }
    });
  };
  
  return { token, apiCall };
};
```

## Token Refresh

When tokens expire, use the refresh token:

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"your-refresh-token"}'
```

## Summary

✅ **Your application already implements Supabase JWT as bearer tokens!**

- **Login** → Get JWT token from Supabase
- **Store** → Client stores the `access_token`
- **Use** → Include `Authorization: Bearer <token>` in requests
- **Protect** → Spring Security automatically validates and authorizes
- **Access** → Controllers receive authenticated user context

The system is fully functional and follows industry-standard bearer token authentication patterns. Your Supabase JWT tokens work seamlessly as Spring Boot bearer tokens for protecting any URL or endpoint you need.
