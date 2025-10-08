package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.security.SupabaseUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo controller showing Spring Security integration with Supabase
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Slf4j
public class SecurityDemoController {

    /**
     * Public endpoint - no authentication required
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "message", "This is a public endpoint",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Protected endpoint - any authenticated user
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, Object>> protectedEndpoint(Authentication authentication) {
        if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            return ResponseEntity.ok(Map.of(
                    "message", "Hello " + userDetails.getDisplayName(),
                    "userId", userDetails.getUserId(),
                    "email", userDetails.getEmail(),
                    "role", userDetails.getRole(),
                    "hasCompleteProfile", userDetails.hasCompleteProfile(),
                    "authorities", userDetails.getAuthorities().stream()
                            .map(Object::toString)
                            .toList()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "message", "Authenticated but user details not available",
                "principal", authentication.getPrincipal().getClass().getSimpleName()
        ));
    }

    /**
     * Mentor-only endpoint using method security
     */
    @GetMapping("/mentor-only")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<Map<String, Object>> mentorOnlyEndpoint(Authentication authentication) {
        SupabaseUserDetails userDetails = (SupabaseUserDetails) authentication.getPrincipal();
        
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the mentor-only area!",
                "mentorName", userDetails.getDisplayName(),
                "userId", userDetails.getUserId()
        ));
    }

    /**
     * Admin-only endpoint
     */
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminOnlyEndpoint(Authentication authentication) {
        SupabaseUserDetails userDetails = (SupabaseUserDetails) authentication.getPrincipal();
        
        return ResponseEntity.ok(Map.of(
                "message", "Admin control panel",
                "adminName", userDetails.getDisplayName(),
                "authorities", userDetails.getAuthorities().stream()
                        .map(Object::toString)
                        .toList()
        ));
    }

    /**
     * Multi-role endpoint - mentors or admins
     */
    @GetMapping("/mentor-or-admin")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> mentorOrAdminEndpoint(Authentication authentication) {
        SupabaseUserDetails userDetails = (SupabaseUserDetails) authentication.getPrincipal();
        
        return ResponseEntity.ok(Map.of(
                "message", "Access granted for mentor or admin",
                "userName", userDetails.getDisplayName(),
                "role", userDetails.getRole(),
                "isMentor", userDetails.isMentor(),
                "isAdmin", userDetails.isAdmin()
        ));
    }

    /**
     * Endpoint that demonstrates programmatic security checks
     */
    @GetMapping("/conditional")
    public ResponseEntity<Map<String, Object>> conditionalEndpoint(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authentication required"));
        }

        Map<String, Object> response = Map.of(
                "userId", userDetails.getUserId(),
                "email", userDetails.getEmail(),
                "displayName", userDetails.getDisplayName()
        );

        if (userDetails.isMentor()) {
            response = Map.of(
                    "message", "Special mentor content",
                    "mentorInfo", response,
                    "canCreateSessions", true
            );
        } else if (userDetails.isMentee()) {
            response = Map.of(
                    "message", "Mentee dashboard content",
                    "menteeInfo", response,
                    "canBookSessions", true
            );
        } else if (userDetails.isAdmin()) {
            response = Map.of(
                    "message", "Admin dashboard",
                    "adminInfo", response,
                    "canManageUsers", true
            );
        } else {
            response = Map.of(
                    "message", "Default user content",
                    "userInfo", response
            );
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to test user profile completion status
     */
    @GetMapping("/profile-status")
    public ResponseEntity<Map<String, Object>> profileStatusEndpoint(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Authentication required"));
        }

        return ResponseEntity.ok(Map.of(
                "hasCompleteProfile", userDetails.hasCompleteProfile(),
                "profileExists", userDetails.getProfile() != null,
                "displayName", userDetails.getDisplayName(),
                "message", userDetails.hasCompleteProfile() 
                        ? "Profile is complete" 
                        : "Please complete your profile"
        ));
    }
}
