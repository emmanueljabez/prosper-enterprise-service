package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.CompleteCompanySignupIntentRequest;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.CompanyAdminRegistrationService;
import com.prosper.prospermentor.service.PasswordResetService;
import com.prosper.prospermentor.service.SupabaseAuthService;
import com.prosper.prospermentor.service.ProfileService;
import com.prosper.prospermentor.service.CompanyService;
import com.prosper.prospermentor.service.SubscriptionService;
import com.prosper.prospermentor.service.notification.MenteeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
    private final CompanyService companyService;
    private final CompanyAdminRegistrationService companyAdminRegistrationService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final MenteeNotificationService menteeNotificationService;
    private final PasswordResetService passwordResetService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

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
     * Get user profile by userId (for viewing other users' profiles)
     * This endpoint allows fetching any user's basic profile by their userId
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<Object> getProfileByUserId(@PathVariable String userId) {
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
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid user ID format"));
        } catch (Exception e) {
            log.error("Error fetching profile by userId: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch profile"));
        }
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
                .flatMap(authResponse -> {
                    try {
                        // Extract user ID from the authentication response
                        String userId = authResponse.get("user").get("id").asText();
                        UUID userUuid = UUID.fromString(userId);

                        // Fetch the profile from database
                        Optional<Map<String, Object>> profileOpt = profileService.getCompleteProfile(userUuid);

                        // Create enhanced response with profile
                        Map<String, Object> enhancedResponse = objectMapper.convertValue(authResponse, Map.class);

                        // Add profile to response if found
                        if (profileOpt.isPresent()) {
                            enhancedResponse.put("profile", profileOpt.get());
                        } else {
                            log.warn("Profile not found for user ID: {}", userId);
                        }

                        if (isFreeTrialRequested(loginRequest)) {
                            enhancedResponse.put("freeTrial", activateFreeTrial(userUuid));
                        }

                        return Mono.just(ResponseEntity.ok((Object) enhancedResponse));
                    } catch (Exception e) {
                        log.error("Error enriching login response with profile: {}", e.getMessage());
                        // Return original response if profile fetch fails
                        return Mono.just(ResponseEntity.ok((Object) authResponse));
                    }
                })
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
        boolean freeTrialRequested = isFreeTrialRequested(signupRequest);
        String emailVerificationRedirectUrl = buildEmailVerificationRedirectUrl(freeTrialRequested);

        return supabaseAuthService.generateSignupConfirmationLink(
                        signupRequest.getEmail(),
                        signupRequest.getPassword(),
                        role,
                        signupRequest.getFirstName(),
                        signupRequest.getLastName(),
                        signupRequest.getPhoneNumber(),
                        emailVerificationRedirectUrl
                )
                .flatMap(authResponse -> {
                    try {
                        JsonNode userNode = authResponse.has("user") ? authResponse.get("user") : authResponse;
                        if (userNode == null || userNode.isNull() || !userNode.hasNonNull("id")) {
                            return Mono.just(ResponseEntity.internalServerError()
                                    .<Object>body(Map.of("error", "Signup provider did not return a user id")));
                        }

                        String userId = userNode.get("id").asText();
                        String email = userNode.hasNonNull("email")
                                ? userNode.get("email").asText()
                                : signupRequest.getEmail().trim().toLowerCase();
                        UUID userUuid = UUID.fromString(userId);

                        var profile = profileService.createProfileWithDetails(
                                userUuid,
                                email,
                                role,
                                signupRequest.getFirstName(),
                                signupRequest.getLastName(),
                                signupRequest.getPhoneNumber(),
                                signupRequest.getDateOfBirth()
                        );

                        Map<String, Object> enhancedResponse = new LinkedHashMap<>();
                        enhancedResponse.put("user", toPublicUserPayload(userNode, email));
                        if (profile.isPresent()) {
                            enhancedResponse.put("profile", profile.get());
                        }
                        enhancedResponse.put("emailVerificationRequired", true);
                        enhancedResponse.put("message", "Mentee account created. Verify your email, then sign in to continue.");

                        if (freeTrialRequested) {
                            enhancedResponse.put("freeTrial", activateFreeTrial(userUuid));
                        }

                        String actionLink = authResponse.hasNonNull("action_link")
                                ? authResponse.get("action_link").asText()
                                : null;
                        if (actionLink == null || actionLink.isBlank()) {
                            return Mono.just(ResponseEntity.internalServerError()
                                    .<Object>body(Map.of("error", "Signup provider did not return a confirmation link")));
                        }

                        menteeNotificationService.sendMenteeEmailConfirmation(
                                email,
                                signupRequest.getFirstName(),
                                freeTrialRequested,
                                toFrontendConfirmationUrl(authResponse, actionLink, freeTrialRequested, role)
                        );

                        log.info("User created successfully: {} with profile", email);
                        return Mono.just(ResponseEntity.ok((Object) enhancedResponse));
                    } catch (Exception e) {
                        log.error("Error creating profile after signup: {}", e.getMessage());
                        return Mono.just(ResponseEntity.internalServerError()
                                .<Object>body(Map.of("error", "Failed to process signup. Please try again or contact support.")));
                    }
                })
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    log.error("Supabase signup error: {}", errorMessage);

                    // Handle user already exists scenarios
                    if (errorMessage.contains("User already registered") ||
                        errorMessage.contains("already exists") ||
                        errorMessage.contains("email_exists") ||
                        errorMessage.contains("422") ||
                        errorMessage.contains("Database error saving new user") ||
                        errorMessage.contains("unexpected_failure")) {
                        return Mono.just(ResponseEntity.status(409)
                                .<Object>body(Map.of("error", "User already exists with this email")));
                    } else if (errorMessage.contains("Invalid email format")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Invalid email format")));
                    }
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Signup service error. Please try again or contact support.")));
                });
    }

    /**
     * Trigger forgot-password email flow.
     */
    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<Object>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email is required")));
        }

        return Mono.fromRunnable(() -> passwordResetService.requestPasswordReset(request.getEmail()))
                .then(Mono.just(ResponseEntity.ok((Object) Map.of(
                        "message", "If an account exists for that email, a password reset link has been sent."
                ))))
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    if (errorMessage != null && errorMessage.contains("Invalid email format")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Invalid email format")));
                    }

                    log.error("Forgot password flow failed: {}", errorMessage);
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Failed to send password reset email")));
                });
    }

    /**
     * Complete password reset using the recovery access token.
     */
    @PostMapping("/reset-password")
    public Mono<ResponseEntity<Object>> resetPassword(@RequestBody ResetPasswordRequest request) {
        String resetToken = request.getResolvedToken();
        if (resetToken == null || resetToken.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Reset token is required")));
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password is required")));
        }

        if (request.getPassword().length() < 8) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password must be at least 8 characters long")));
        }

        return passwordResetService.resetPasswordWithToken(resetToken, request.getPassword())
                .then(Mono.just(ResponseEntity.ok((Object) Map.of(
                        "message", "Password updated successfully"
                ))))
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    log.error("Reset password flow failed: {}", errorMessage);

                    if (errorMessage != null && errorMessage.contains("Reset link is invalid or has expired")) {
                        return Mono.just(ResponseEntity.status(401)
                                .<Object>body(Map.of("error", "Reset link is invalid or has expired")));
                    }

                    if (errorMessage != null && errorMessage.contains("Password")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", errorMessage)));
                    }

                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Failed to reset password")));
                });
    }

    /**
     * Complete invitation signup - creates user in Supabase and links to company
     */
    @PostMapping("/complete-invitation-signup")
    public Mono<ResponseEntity<Object>> completeInvitationSignup(@RequestBody InvitationSignupRequest request) {
        if (request.getEmail() == null || request.getPassword() == null || request.getInvitationToken() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email, password, and invitation token are required")));
        }

        // Validate required profile fields
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "First name is required")));
        }

        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Last name is required")));
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Phone number is required")));
        }

        // Validate password strength
        if (request.getPassword().length() < 6) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password must be at least 6 characters long")));
        }

        // Verify invitation token first
        var verificationResponse = companyService.verifyInvitationToken(request.getInvitationToken());
        if (!verificationResponse.isSuccess()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", verificationResponse.getMessage())));
        }

        // Try to create user, or sign in if already exists (more flexible for invitations)
        return supabaseAuthService.createMinimalUser(
                request.getEmail(),
                request.getPassword(),
                "mentee",
                request.getFirstName(),
                request.getLastName()
        )
                .onErrorResume(createError -> {
                    String errorMessage = createError.getMessage();

                    // If user already exists, try signing them in instead
                    if (errorMessage.contains("User already registered") ||
                        errorMessage.contains("422") ||
                        errorMessage.contains("Database error creating new user") ||
                        errorMessage.contains("unexpected_failure")) {

                        log.info("User already exists, attempting to sign in: {}", request.getEmail());
                        return supabaseAuthService.signInWithPassword(request.getEmail(), request.getPassword())
                                .map(authResponse -> {
                                    // Return a special marker to indicate this was a sign-in, not creation
                                    return authResponse;
                                });
                    }

                    // For other errors, propagate them
                    return Mono.error(createError);
                })
                .flatMap(userNodeOrAuthResponse -> {
                    try {
                        // Determine if this is a newly created user or existing user sign-in
                        UUID userUuid;
                        String email;
                        Mono<JsonNode> authTokensMono;

                        if (userNodeOrAuthResponse.has("access_token")) {
                            // This is a sign-in response (existing user)
                            log.debug("Existing user signed in: {}", userNodeOrAuthResponse.get("user").get("email").asText());
                            JsonNode user = userNodeOrAuthResponse.get("user");
                            userUuid = UUID.fromString(user.get("id").asText());
                            email = user.get("email").asText();
                            authTokensMono = Mono.just(userNodeOrAuthResponse);
                        } else {
                            // This is a user creation response (new user)
                            log.debug("New user created: {}", userNodeOrAuthResponse.get("email").asText());
                            userUuid = UUID.fromString(userNodeOrAuthResponse.get("id").asText());
                            email = userNodeOrAuthResponse.get("email").asText();

                            // Need to sign in the newly created user
                            log.info("Signing in newly created user: {}", email);
                            authTokensMono = supabaseAuthService.signInWithPassword(email, request.getPassword());
                        }

                        return authTokensMono.flatMap(authResponse -> {
                            try {
                                log.debug("User authenticated, completing profile setup: {}", email);

                                // Use the orchestrated service method to complete signup
                                var signupResponse = companyService.completeInvitationSignupWithProfile(
                                        request.getInvitationToken(),
                                        userUuid,
                                        email,
                                        request.getFirstName(),
                                        request.getLastName(),
                                        request.getPhoneNumber(),
                                        request.getDateOfBirth()
                                );

                                if (!signupResponse.isSuccess()) {
                                    log.error("Failed to complete invitation signup: {}", signupResponse.getMessage());
                                    return Mono.just(ResponseEntity.badRequest()
                                            .<Object>body(Map.of("error", signupResponse.getMessage())));
                                }

                                // Prepare enhanced response with auth tokens and profile
                                Map<String, Object> enhancedResponse = objectMapper.convertValue(authResponse, Map.class);
                                enhancedResponse.put("profile", signupResponse.getData().get("profile"));
                                enhancedResponse.put("company", signupResponse.getData().get("company"));

                                log.info("Invitation signup completed successfully for: {}", email);
                                return Mono.just(ResponseEntity.ok((Object) enhancedResponse));

                            } catch (Exception e) {
                                log.error("Error completing invitation signup: {}", e.getMessage(), e);
                                return Mono.just(ResponseEntity.internalServerError()
                                        .<Object>body(Map.of("error", "Failed to complete signup: " + e.getMessage())));
                            }
                        });

                    } catch (Exception e) {
                        log.error("Error processing user data: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.internalServerError()
                                .<Object>body(Map.of("error", "Failed to process user data: " + e.getMessage())));
                    }
                })
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    log.error("Invitation signup error: {}", errorMessage);

                    if (errorMessage.contains("Invalid login credentials") || errorMessage.contains("invalid_credentials")) {
                        return Mono.just(ResponseEntity.status(401)
                                .<Object>body(Map.of(
                                    "error", "An account with this email already exists. Please use your existing password to complete the invitation signup.",
                                    "errorCode", "EXISTING_USER_INVALID_PASSWORD"
                                )));
                    } else if (errorMessage.contains("Invalid email format")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Invalid email format")));
                    }
                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Signup service error. Please try again or contact support.")));
                });
    }

    @PostMapping("/complete-company-registration")
    public Mono<ResponseEntity<Object>> completeCompanyRegistration(@RequestBody CompanyRegistrationSignupRequest request) {
        if (request.getEmail() == null || request.getPassword() == null || request.getRegistrationToken() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email, password, and registration token are required")));
        }

        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "First name is required")));
        }

        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Last name is required")));
        }

        if (request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Phone number is required")));
        }

        if (request.getPassword().length() < 6) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password must be at least 6 characters long")));
        }

        CompleteCompanySignupIntentRequest delegatedRequest = new CompleteCompanySignupIntentRequest();
        delegatedRequest.setEmail(request.getEmail());
        delegatedRequest.setPassword(request.getPassword());
        delegatedRequest.setFirstName(request.getFirstName());
        delegatedRequest.setLastName(request.getLastName());
        delegatedRequest.setPhoneNumber(request.getPhoneNumber());
        delegatedRequest.setDateOfBirth(request.getDateOfBirth());
        return companyAdminRegistrationService.completeFromRegistrationToken(request.getRegistrationToken(), delegatedRequest);
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

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.trim().isEmpty()
                ? "http://localhost:3000"
                : value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private String buildEmailVerificationRedirectUrl(boolean freeTrialRequested) {
        String base = normalizeBaseUrl(frontendUrl) + "/auth/login?email_verified=1";
        if (!freeTrialRequested) {
            return base;
        }
        return base + "&audience=mentee&trial=1&product=FREE_TRIAL";
    }

    private Map<String, Object> toPublicUserPayload(JsonNode userNode, String fallbackEmail) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userNode.get("id").asText());
        user.put("email", userNode.hasNonNull("email") ? userNode.get("email").asText() : fallbackEmail);

        if (userNode.hasNonNull("email_confirmed_at")) {
            user.put("emailConfirmedAt", userNode.get("email_confirmed_at").asText());
        }

        return user;
    }

    private String toFrontendConfirmationUrl(JsonNode signupResponse,
                                             String actionLink,
                                             boolean freeTrialRequested,
                                             String role) {
        String tokenHash = resolveTokenHash(signupResponse, actionLink);
        String type = resolveVerificationType(signupResponse, actionLink);
        StringBuilder url = new StringBuilder(normalizeBaseUrl(frontendUrl))
                .append("/auth/confirm-email?token_hash=")
                .append(URLEncoder.encode(tokenHash, StandardCharsets.UTF_8))
                .append("&type=")
                .append(URLEncoder.encode(type, StandardCharsets.UTF_8));

        if (freeTrialRequested) {
            url.append("&audience=mentee&trial=1&product=FREE_TRIAL");
        } else if (role != null && !role.isBlank()) {
            url.append("&audience=")
                    .append(URLEncoder.encode(role.trim().toLowerCase(), StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    private String resolveTokenHash(JsonNode signupResponse, String actionLink) {
        if (signupResponse.hasNonNull("hashed_token")) {
            return signupResponse.get("hashed_token").asText();
        }
        if (signupResponse.hasNonNull("token_hash")) {
            return signupResponse.get("token_hash").asText();
        }
        String token = getQueryParam(actionLink, "token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        throw new IllegalStateException("Signup provider did not return a confirmation token");
    }

    private String resolveVerificationType(JsonNode signupResponse, String actionLink) {
        if (signupResponse.hasNonNull("verification_type")) {
            return signupResponse.get("verification_type").asText();
        }
        String type = getQueryParam(actionLink, "type");
        return type == null || type.isBlank() ? "signup" : type;
    }

    private String getQueryParam(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return null;
        }
        int fragmentStart = url.indexOf('#', queryStart);
        String query = fragmentStart >= 0 ? url.substring(queryStart + 1, fragmentStart) : url.substring(queryStart + 1);
        for (String part : query.split("&")) {
            int equalsIndex = part.indexOf('=');
            String key = equalsIndex >= 0 ? part.substring(0, equalsIndex) : part;
            if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                String value = equalsIndex >= 0 ? part.substring(equalsIndex + 1) : "";
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private boolean isFreeTrialRequested(LoginRequest request) {
        return request != null && isFreeTrialRequested(request.getProduct(), request.getTrial());
    }

    private boolean isFreeTrialRequested(SignupRequest request) {
        return request != null && isFreeTrialRequested(request.getProduct(), request.getTrial());
    }

    private boolean isFreeTrialRequested(String product, Boolean trial) {
        return Boolean.TRUE.equals(trial)
                || "FREE_TRIAL".equalsIgnoreCase(String.valueOf(product).trim());
    }

    private Map<String, Object> activateFreeTrial(UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requested", true);

        try {
            ApiResponse<Subscription> response = subscriptionService.activateFreeTrial(userId);
            payload.put("activated", response.isSuccess());
            payload.put("message", response.getMessage());
            payload.put("sessionDurationMinutes", SubscriptionService.TRIAL_SESSION_DURATION_MINUTES);
            if (response.getData() != null) {
                payload.put("subscriptionId", response.getData().getId());
                payload.put("status", response.getData().getStatus());
                payload.put("remainingSessions", response.getData().getRemainingSessionsCount());
            }
        } catch (Exception error) {
            log.error("Failed to activate free trial for user {}: {}", userId, error.getMessage(), error);
            payload.put("activated", false);
            payload.put("message", "Free trial could not be activated. Please contact support.");
            payload.put("sessionDurationMinutes", SubscriptionService.TRIAL_SESSION_DURATION_MINUTES);
        }

        return payload;
    }

    // Request DTOs
    public static class LoginRequest {
        private String email;
        private String password;
        private String product;
        private Boolean trial;
        private String audience;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getProduct() { return product; }
        public void setProduct(String product) { this.product = product; }
        public Boolean getTrial() { return trial; }
        public void setTrial(Boolean trial) { this.trial = trial; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
    }

    public static class SignupRequest {
        private String email;
        private String password;
        private String role;
        private String product;
        private Boolean trial;
        private String audience;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String dateOfBirth;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getProduct() { return product; }
        public void setProduct(String product) { this.product = product; }
        public Boolean getTrial() { return trial; }
        public void setTrial(Boolean trial) { this.trial = trial; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class InvitationSignupRequest {
        private String email;
        private String password;
        private String invitationToken;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String dateOfBirth;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getInvitationToken() { return invitationToken; }
        public void setInvitationToken(String invitationToken) { this.invitationToken = invitationToken; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    }

    public static class ForgotPasswordRequest {
        private String email;
        private String redirectTo;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRedirectTo() { return redirectTo; }
        public void setRedirectTo(String redirectTo) { this.redirectTo = redirectTo; }
    }

    public static class ResetPasswordRequest {
        private String token;
        private String password;
        private String accessToken;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getResolvedToken() {
            return token != null && !token.trim().isEmpty() ? token : accessToken;
        }
    }

    public static class CompanyRegistrationSignupRequest {
        private String email;
        private String password;
        private String registrationToken;
        private String firstName;
        private String lastName;
        private String phoneNumber;
        private String dateOfBirth;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRegistrationToken() { return registrationToken; }
        public void setRegistrationToken(String registrationToken) { this.registrationToken = registrationToken; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
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
