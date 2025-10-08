package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.SupabaseAuthService;
import com.prosper.prospermentor.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for authentication-related endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SupabaseAuthService supabaseAuthService;
    private final ProfileService profileService;

    /**
     * Get current authenticated user's basic profile (from JWT)
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        if (authentication != null) {
            // Handle both UserDetails and legacy SupabaseUserPrincipal
            if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
                Map<String, Object> profile = Map.of(
                        "userId", userDetails.getUserId(),
                        "email", userDetails.getEmail(),
                        "role", userDetails.getRole(),
                        "displayName", userDetails.getDisplayName(),
                        "hasCompleteProfile", userDetails.hasCompleteProfile(),
                        "authenticated", true
                );
                return ResponseEntity.ok(profile);
            } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
                Map<String, Object> profile = Map.of(
                        "userId", principal.getUserId(),
                        "email", principal.getEmail(),
                        "role", principal.getRole(),
                        "authenticated", true
                );
                return ResponseEntity.ok(profile);
            }
        }
        
        return ResponseEntity.ok(Map.of("authenticated", false));
    }

    /**
     * Get complete profile details from database
     */
    @GetMapping("/profile/complete")
    public ResponseEntity<Object> getCompleteProfile(Authentication authentication) {
        if (authentication != null) {
            try {
                String userId = null;
                
                // Handle both UserDetails and legacy SupabaseUserPrincipal
                if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
                    userId = userDetails.getUserId();
                } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
                    userId = principal.getUserId();
                }
                
                if (userId != null) {
                    UUID userUuid = UUID.fromString(userId);
                    var completeProfile = profileService.getCompleteProfile(userUuid);
                    
                    if (completeProfile.isPresent()) {
                        return ResponseEntity.ok(completeProfile.get());
                    } else {
                        return ResponseEntity.status(404)
                                .body(Map.of("error", "Profile not found in database"));
                    }
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error fetching complete profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to fetch profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Get basic profile details from database
     */
    @GetMapping("/profile/basic")
    public ResponseEntity<Object> getBasicProfile(Authentication authentication) {
        if (authentication != null) {
            try {
                String userId = null;
                
                // Handle both UserDetails and legacy SupabaseUserPrincipal
                if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
                    userId = userDetails.getUserId();
                } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
                    userId = principal.getUserId();
                }
                
                if (userId != null) {
                    UUID userUuid = UUID.fromString(userId);
                    var profile = profileService.getBasicProfile(userUuid);
                    
                    if (profile.isPresent()) {
                        return ResponseEntity.ok(profile.get());
                    } else {
                        return ResponseEntity.status(404)
                                .body(Map.of("error", "Profile not found in database"));
                    }
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error fetching basic profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to fetch profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Update profile information
     */
    @PutMapping("/profile/update")
    public ResponseEntity<Object> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, Object> updates) {
        
        if (authentication != null) {
            try {
                String userId = null;
                
                // Handle both UserDetails and legacy SupabaseUserPrincipal
                if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
                    userId = userDetails.getUserId();
                } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
                    userId = principal.getUserId();
                }
                
                if (userId != null) {
                    UUID userUuid = UUID.fromString(userId);
                    var updatedProfile = profileService.updateProfile(userUuid, updates);
                    
                    if (updatedProfile.isPresent()) {
                        return ResponseEntity.ok(updatedProfile.get());
                    } else {
                        return ResponseEntity.status(404)
                                .body(Map.of("error", "Profile not found in database"));
                    }
                }
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error updating profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to update profile: " + e.getMessage()));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Get detailed user information from Supabase
     */
    @GetMapping("/user-details")
    public Mono<ResponseEntity<Object>> getUserDetails(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader) {
        
        if (authentication != null && authentication.getPrincipal() instanceof SupabaseUserPrincipal) {
            String accessToken = authHeader.replace("Bearer ", "");
            
            return supabaseAuthService.getUserDetails(accessToken)
                    .map(result -> ResponseEntity.ok((Object) result))
                    .onErrorReturn(ResponseEntity.internalServerError().<Object>build());
        }
        
        return Mono.just(ResponseEntity.status(401).<Object>build());
    }

    /**
     * Update user metadata (role, etc.)
     */
    @PutMapping("/update-metadata")
    public Mono<ResponseEntity<Object>> updateMetadata(
            Authentication authentication,
            @RequestBody Map<String, Object> metadata) {
        
        if (authentication != null) {
            String userId = null;
            
            // Handle both UserDetails and legacy SupabaseUserPrincipal
            if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
                userId = userDetails.getUserId();
            } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
                userId = principal.getUserId();
            }
            
            if (userId != null) {
                return supabaseAuthService.updateUserMetadata(userId, metadata)
                        .map(result -> ResponseEntity.ok((Object) result))
                        .onErrorReturn(ResponseEntity.internalServerError().<Object>build());
            }
        }
        
        return Mono.just(ResponseEntity.status(401).<Object>build());
    }

    /**
     * Login with email and password
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<Object>> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email and password are required")));
        }

        return supabaseAuthService.signInWithPassword(loginRequest.getEmail(), loginRequest.getPassword())
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    if (errorMessage.contains("Invalid login credentials") || errorMessage.contains("400")) {
                        return Mono.just(ResponseEntity.status(401)
                                .<Object>body(Map.of("error", "Invalid email or password")));
                    }
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Authentication service error")));
                });
    }

    /**
     * Sign up new user with email and password
     */
    @PostMapping("/signup")
    public Mono<ResponseEntity<Object>> signup(@RequestBody SignupRequest signupRequest) {
        if (signupRequest.getEmail() == null || signupRequest.getPassword() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email and password are required")));
        }

        // Validate password strength
        if (signupRequest.getPassword().length() < 6) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password must be at least 6 characters long")));
        }

        String role = signupRequest.getRole() != null ? signupRequest.getRole() : "mentee";

        return supabaseAuthService.signUpWithPassword(signupRequest.getEmail(), signupRequest.getPassword(), role)
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    if (errorMessage.contains("User already registered") || errorMessage.contains("422")) {
                        return Mono.just(ResponseEntity.status(409)
                                .<Object>body(Map.of("error", "User already exists with this email")));
                    } else if (errorMessage.contains("Invalid email format")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Invalid email format")));
                    }
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Signup service error")));
                });
    }

    /**
     * Refresh access token
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refreshToken(@RequestBody RefreshTokenRequest refreshRequest) {
        if (refreshRequest.getRefreshToken() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Refresh token is required")));
        }

        return supabaseAuthService.refreshToken(refreshRequest.getRefreshToken())
                .map(result -> ResponseEntity.ok((Object) result))
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    if (errorMessage.contains("Invalid refresh token") || errorMessage.contains("401")) {
                        return Mono.just(ResponseEntity.status(401)
                                .<Object>body(Map.of("error", "Invalid or expired refresh token")));
                    }
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Token refresh service error")));
                });
    }

    /**
     * Logout user
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Object>> logout(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader) {
        
        if (authentication != null && authentication.getPrincipal() instanceof SupabaseUserPrincipal) {
            String accessToken = authHeader.replace("Bearer ", "");
            
            return supabaseAuthService.signOut(accessToken)
                    .then(Mono.just(ResponseEntity.ok((Object) Map.of("message", "Successfully logged out"))))
                    .onErrorReturn(ResponseEntity.ok((Object) Map.of("message", "Logged out (token may have already expired)")));
        }
        
        return Mono.just(ResponseEntity.ok((Object) Map.of("message", "No active session to logout")));
    }

    // Request DTOs
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class SignupRequest {
        private String email;
        private String password;
        private String role;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    /**
     * Helper method to extract user information from authentication
     */
    private UserInfo extractUserInfo(Authentication authentication) {
        if (authentication == null) return null;
        
        if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            return new UserInfo(userDetails.getUserId(), userDetails.getEmail(), userDetails.getRole());
        } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
            return new UserInfo(principal.getUserId(), principal.getEmail(), principal.getRole());
        }
        
        return null;
    }

    /**
     * Helper class to hold user information
     */
    private record UserInfo(String userId, String email, String role) {}
}

