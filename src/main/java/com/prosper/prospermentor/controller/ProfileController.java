package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.MenteeProfile;
import com.prosper.prospermentor.entity.MentorProfile;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for profile-related endpoints
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    /**
     * Helper method to extract user ID from authentication
     */
    private String extractUserId(Authentication authentication) {
        if (authentication == null) return null;
        
        if (authentication.getPrincipal() instanceof SupabaseUserDetails userDetails) {
            return userDetails.getUserId();
        } else if (authentication.getPrincipal() instanceof SupabaseUserPrincipal principal) {
            return principal.getUserId();
        }
        
        return null;
    }

    /**
     * Get complete profile for the authenticated user
     */
    @GetMapping("/me")
    public ResponseEntity<Object> getMyProfile(Authentication authentication) {
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
                                .body(Map.of("error", "Profile not found"));
                    }
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format", e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error fetching profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to fetch profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Get basic profile information for the authenticated user
     */
    @GetMapping("/me/basic")
    public ResponseEntity<Object> getMyBasicProfile(Authentication authentication) {
        String userId = extractUserId(authentication);
        if (userId != null) {
            try {
                UUID userUuid = UUID.fromString(userId);
                var profile = profileService.getBasicProfile(userUuid);
                
                if (profile.isPresent()) {
                    return ResponseEntity.ok(profile.get());
                } else {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Profile not found"));
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format: {}", userId, e);
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
     * Get mentee profile for the authenticated user (if they are a mentee)
     */
    @GetMapping("/me/mentee")
    public ResponseEntity<Object> getMyMenteeProfile(Authentication authentication) {
        String userId = extractUserId(authentication);
        if (userId != null) {
            try {
                UUID userUuid = UUID.fromString(userId);
                
                // Check if user is a mentee
                var basicProfile = profileService.getBasicProfile(userUuid);
                if (basicProfile.isEmpty()) {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Profile not found"));
                }
                
                if (!"MENTEE".equalsIgnoreCase(basicProfile.get().getRole())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "User is not a mentee"));
                }
                
                var menteeProfile = profileService.getMenteeProfile(userUuid);
                if (menteeProfile.isPresent()) {
                    return ResponseEntity.ok(menteeProfile.get());
                } else {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Mentee profile not found"));
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format: {}", userId, e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error fetching mentee profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to fetch profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Get mentor profile for the authenticated user (if they are a mentor)
     */
    @GetMapping("/me/mentor")
    public ResponseEntity<Object> getMyMentorProfile(Authentication authentication) {
        String userId = extractUserId(authentication);
        if (userId != null) {
            try {
                UUID userUuid = UUID.fromString(userId);
                
                // Check if user is a mentor
                var basicProfile = profileService.getBasicProfile(userUuid);
                if (basicProfile.isEmpty()) {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Profile not found"));
                }
                
                if (!"MENTOR".equalsIgnoreCase(basicProfile.get().getRole())) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "User is not a mentor"));
                }
                
                var mentorProfile = profileService.getMentorProfile(userUuid);
                if (mentorProfile.isPresent()) {
                    return ResponseEntity.ok(mentorProfile.get());
                } else {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Mentor profile not found"));
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format: {}", userId, e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error fetching mentor profile: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to fetch profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Update basic profile information
     */
    @PutMapping("/me")
    public ResponseEntity<Object> updateMyProfile(
            Authentication authentication,
            @RequestBody Map<String, Object> updates) {
        
        String userId = extractUserId(authentication);
        if (userId != null) {
            try {
                UUID userUuid = UUID.fromString(userId);
                var updatedProfile = profileService.updateProfile(userUuid, updates);
                
                if (updatedProfile.isPresent()) {
                    return ResponseEntity.ok(updatedProfile.get());
                } else {
                    return ResponseEntity.status(404)
                            .body(Map.of("error", "Profile not found"));
                }
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format: {}", userId, e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Failed to update profile for user: {}", userId, e);
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to update profile: " + e.getMessage()));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Check if profile exists for the authenticated user
     */
    @GetMapping("/me/exists")
    public ResponseEntity<Object> checkMyProfileExists(Authentication authentication) {
        String userId = extractUserId(authentication);
        if (userId != null) {
            try {
                UUID userUuid = UUID.fromString(userId);
                boolean exists = profileService.profileExists(userUuid);
                
                return ResponseEntity.ok(Map.of(
                        "exists", exists,
                        "userId", userUuid.toString()
                ));
            } catch (IllegalArgumentException e) {
                log.error("Invalid user ID format: {}", userId, e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid user ID format"));
            } catch (Exception e) {
                log.error("Error checking profile existence: {}", e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to check profile"));
            }
        }
        
        return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required"));
    }

    /**
     * Get all mentors with pagination and filters (public endpoint)
     */
    @GetMapping("/mentors")
    public ResponseEntity<Object> getAllMentors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean isVerified,
            @RequestParam(required = false) String searchTerm) {
        try {
            log.info("Fetching mentors with pagination - page: {}, size: {}, isVerified: {}, searchTerm: {}",
                     page, size, isVerified, searchTerm);

            Page<Profile> mentorsPage = profileService.getAllMentorsPaginated(page, size, isVerified, searchTerm);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mentors", mentorsPage.getContent());
            response.put("currentPage", mentorsPage.getNumber());
            response.put("totalPages", mentorsPage.getTotalPages());
            response.put("totalItems", mentorsPage.getTotalElements());
            response.put("hasNext", mentorsPage.hasNext());
            response.put("hasPrevious", mentorsPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching mentors with pagination: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to fetch mentors: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Get a single mentor profile by ID.
     */
    @GetMapping("/mentors/{mentorId:[0-9a-fA-F\\-]{36}}")
    public ResponseEntity<Object> getMentorById(@PathVariable UUID mentorId) {
        try {
            log.info("Fetching mentor profile by ID: {}", mentorId);

            var mentorProfile = profileService.getCompleteProfile(mentorId);
            if (mentorProfile.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Mentor profile not found"));
            }

            Object role = mentorProfile.get().get("role");
            if (role == null || !"MENTOR".equalsIgnoreCase(role.toString())) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "Mentor profile not found"));
            }

            return ResponseEntity.ok(mentorProfile.get());
        } catch (Exception e) {
            log.error("Error fetching mentor profile {}: {}", mentorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch mentor profile"));
        }
    }

    /**
     * Get all basic mentor profiles (public endpoint)
     */
    @GetMapping("/mentors/basic")
    public ResponseEntity<Object> getAllMentorProfiles() {
        try {
            log.info("Fetching all basic mentor profiles");
            List<Profile> mentors = profileService.getAllMentorProfiles();
            
            return ResponseEntity.ok(Map.of(
                    "mentors", mentors,
                    "count", mentors.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching mentor profiles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch mentor profiles"));
        }
    }
}
