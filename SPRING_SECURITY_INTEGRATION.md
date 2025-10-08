# Spring Security Integration with Supabase Authentication

This document explains how Supabase authentication is fully integrated with Spring Boot's security mechanism.

## Overview

The integration provides:
- **Native Spring Security support** for Supabase JWT tokens
- **UserDetails implementation** for complete Spring Security compatibility
- **Custom Authentication Provider** for Supabase-specific authentication logic
- **Automatic profile loading** from database when available
- **Role-based access control** using Spring Security's `@PreAuthorize` and method security
- **Seamless fallback** to JWT-only authentication when profile doesn't exist

## Architecture Components

### 1. SupabaseUserDetails
**Location**: `src/main/java/com/prosper/prospermentor/security/SupabaseUserDetails.java`

Implements Spring Security's `UserDetails` interface:
```java
public class SupabaseUserDetails implements UserDetails {
    private final String userId;
    private final String email;
    private final String role;
    private final Profile profile; // Optional - from database
    private final boolean enabled;
    private final boolean emailVerified;
    
    // Spring Security methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities();
    @Override
    public String getUsername(); // Returns email
    @Override
    public boolean isEnabled();
    // ... other UserDetails methods
    
    // Custom methods
    public UUID getUserIdAsUuid();
    public boolean hasRole(String roleName);
    public boolean isMentor();
    public boolean isMentee();
    public String getDisplayName();
    public boolean hasCompleteProfile();
}
```

### 2. SupabaseUserDetailsService
**Location**: `src/main/java/com/prosper/prospermentor/security/SupabaseUserDetailsService.java`

Implements Spring Security's `UserDetailsService`:
```java
@Service
public class SupabaseUserDetailsService implements UserDetailsService {
    // Load user by email (standard Spring Security method)
    @Override
    public UserDetails loadUserByUsername(String email);
    
    // Load user by UUID (Supabase user ID)
    public UserDetails loadUserByUserId(UUID userId);
    
    // Create UserDetails from JWT when profile doesn't exist
    public UserDetails createUserDetailsFromJwt(String userId, String email, String role);
    
    // Check if user exists in database
    public boolean userExists(String email);
    public boolean userExistsById(UUID userId);
}
```

### 3. SupabaseAuthenticationProvider
**Location**: `src/main/java/com/prosper/prospermentor/security/SupabaseAuthenticationProvider.java`

Custom authentication provider for Spring Security:
```java
@Component
public class SupabaseAuthenticationProvider implements AuthenticationProvider {
    @Override
    public Authentication authenticate(Authentication authentication);
    
    @Override
    public boolean supports(Class<?> authentication);
    
    private Authentication authenticateWithJwt(String jwtToken);
}
```

### 4. Enhanced JwtAuthenticationFilter
**Location**: `src/main/java/com/prosper/prospermentor/security/JwtAuthenticationFilter.java`

Enhanced to work with UserDetailsService:
- Validates JWT tokens
- Loads user from database if profile exists
- Falls back to JWT-only UserDetails if profile doesn't exist
- Sets Spring Security context with proper UserDetails

### 5. Enhanced SecurityConfig
**Location**: `src/main/java/com/prosper/prospermentor/config/SecurityConfig.java`

Configured with custom authentication provider:
```java
@Bean
public AuthenticationManager authenticationManager() {
    return new ProviderManager(List.of(supabaseAuthenticationProvider));
}
```

## Authentication Flow

### 1. User Login Flow
```
1. User sends login request to /api/auth/login
2. SupabaseAuthService calls Supabase Auth API
3. Supabase returns JWT token with user info
4. JWT token is returned to client
```

### 2. Authenticated Request Flow
```
1. Client sends request with JWT token in Authorization header
2. JwtAuthenticationFilter intercepts request
3. JWT token is validated using JwtUtil
4. UserDetailsService tries to load user from database
   - If profile exists: Creates SupabaseUserDetails with full profile
   - If profile doesn't exist: Creates SupabaseUserDetails from JWT only
5. Spring Security context is set with UserDetails
6. Request proceeds with authenticated user
```

## Using Spring Security Features

### 1. Method Security
Enable method security in your configuration and use annotations:

```java
@PreAuthorize("hasRole('MENTOR')")
@GetMapping("/mentor-only")
public ResponseEntity<?> mentorOnlyEndpoint() {
    return ResponseEntity.ok("Only mentors can access this");
}

@PreAuthorize("hasRole('ADMIN') or hasRole('MENTOR')")
@GetMapping("/mentor-or-admin")
public ResponseEntity<?> mentorOrAdminEndpoint() {
    return ResponseEntity.ok("Mentors or admins can access this");
}
```

### 2. Accessing Authenticated User
In your controllers, you can now access the full UserDetails:

```java
@GetMapping("/my-info")
public ResponseEntity<?> getMyInfo(Authentication authentication) {
    if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
            "userId", userDetails.getUserId(),
            "email", userDetails.getEmail(),
            "displayName", userDetails.getDisplayName(),
            "role", userDetails.getRole(),
            "hasCompleteProfile", userDetails.hasCompleteProfile(),
            "isMentor", userDetails.isMentor(),
            "profile", userDetails.getProfile()
        ));
    }
    return ResponseEntity.status(401).body("Not authenticated");
}
```

### 3. Role-Based Access in Controllers
```java
@RestController
@RequestMapping("/api/mentor")
@PreAuthorize("hasRole('MENTOR')") // Class-level security
public class MentorController {
    
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(Authentication auth) {
        SupabaseUserDetails user = (SupabaseUserDetails) auth.getPrincipal();
        // Only mentors can reach here
        return ResponseEntity.ok("Mentor dashboard for: " + user.getDisplayName());
    }
}
```

### 4. Programmatic Security Checks
```java
@Service
public class SomeService {
    
    public void doSomething() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth.getPrincipal() instanceof SupabaseUserDetails user) {
            if (user.isMentor()) {
                // Mentor-specific logic
            } else if (user.isMentee()) {
                // Mentee-specific logic
            }
        }
    }
}
```

## Benefits of Integration

### 1. **Native Spring Security Support**
- Full compatibility with Spring Security annotations
- Works with method security
- Integrates with Spring Security's authentication events
- Compatible with Spring Security testing

### 2. **Database Integration**
- Automatically loads user profile from database when available
- Graceful fallback to JWT-only mode when profile doesn't exist
- Caches user information during request lifecycle

### 3. **Role-Based Security**
- Automatic role mapping from Supabase to Spring Security authorities
- Support for multiple roles and permissions
- Easy role checking with built-in methods

### 4. **Extensibility**
- Easy to add custom authorities or permissions
- Can extend UserDetails for additional user information
- Pluggable authentication providers

## Testing Authentication

### 1. Test Login and Get User Info
```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"testpass123"}'

# Use the access_token from response
export TOKEN="your-access-token-here"

# Get enhanced profile info
curl -X GET http://localhost:8080/api/auth/profile \
  -H "Authorization: Bearer $TOKEN"
```

**Enhanced Response:**
```json
{
  "userId": "uuid-here",
  "email": "test@example.com",
  "role": "MENTEE",
  "displayName": "John Doe",
  "hasCompleteProfile": true,
  "authenticated": true
}
```

### 2. Test Role-Based Access
```bash
# This will work if user is a mentor
curl -X GET http://localhost:8080/api/mentor/dashboard \
  -H "Authorization: Bearer $TOKEN"

# This will return 403 if user is not a mentor
```

## Migration Notes

### Backward Compatibility
The integration maintains backward compatibility with existing `SupabaseUserPrincipal` usage. Controllers can handle both:

```java
public ResponseEntity<?> handleBoth(Authentication auth) {
    if (auth.getPrincipal() instanceof SupabaseUserDetails userDetails) {
        // New enhanced UserDetails
        return ResponseEntity.ok(userDetails.getDisplayName());
    } else if (auth.getPrincipal() instanceof SupabaseUserPrincipal principal) {
        // Legacy support
        return ResponseEntity.ok(principal.getEmail());
    }
    return ResponseEntity.status(401).build();
}
```

### Gradual Migration
You can gradually migrate your controllers to use the new `SupabaseUserDetails` while maintaining compatibility with existing code.

## Next Steps

1. **Enable Method Security**: Add `@EnableMethodSecurity` to enable `@PreAuthorize` annotations
2. **Add Custom Authorities**: Extend the system to support more granular permissions
3. **Implement Caching**: Add caching for user details to improve performance
4. **Add Security Events**: Listen to Spring Security authentication events for logging/monitoring
5. **Testing Integration**: Use Spring Security test support for integration tests
