package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for interacting with Supabase Auth API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupabaseAuthService {

    private final WebClient supabaseWebClient;
    private final WebClient supabaseAdminWebClient;
    private final ObjectMapper objectMapper;
    private final ProfileService profileService;

    /**
     * Get user details from Supabase using access token
     */
    public Mono<JsonNode> getUserDetails(String accessToken) {
        return supabaseWebClient
                .get()
                .uri("/auth/v1/user")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(user -> log.debug("Successfully retrieved user details"))
                .doOnError(error -> log.error("Failed to retrieve user details: {}", error.getMessage()));
    }

    /**
     * Update user metadata (requires service role key)
     */
    public Mono<JsonNode> updateUserMetadata(String userId, Map<String, Object> metadata) {
        Map<String, Object> requestBody = Map.of("user_metadata", metadata);
        
        return supabaseAdminWebClient
                .put()
                .uri("/auth/v1/admin/users/{userId}", userId)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.debug("Successfully updated user metadata for user: {}", userId))
                .doOnError(error -> log.error("Failed to update user metadata: {}", error.getMessage()));
    }

    /**
     * Get user by ID (admin operation)
     */
    public Mono<JsonNode> getUserById(String userId) {
        return supabaseAdminWebClient
                .get()
                .uri("/auth/v1/admin/users/{userId}", userId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(user -> log.debug("Successfully retrieved user by ID: {}", userId))
                .doOnError(error -> log.error("Failed to retrieve user by ID: {}", error.getMessage()));
    }

    /**
     * List all users (admin operation with pagination)
     */
    public Mono<JsonNode> listUsers(int page, int perPage) {
        return supabaseAdminWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/auth/v1/admin/users")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(users -> log.debug("Successfully retrieved users list"))
                .doOnError(error -> log.error("Failed to retrieve users list: {}", error.getMessage()));
    }

    /**
     * Delete user (admin operation)
     */
    public Mono<Void> deleteUser(String userId) {
        return supabaseAdminWebClient
                .delete()
                .uri("/auth/v1/admin/users/{userId}", userId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(result -> log.info("Successfully deleted user: {}", userId))
                .doOnError(error -> log.error("Failed to delete user: {}", error.getMessage()));
    }

    /**
     * Create user (admin operation)
     */
    public Mono<JsonNode> createUser(String email, String password, Map<String, Object> userMetadata) {
        // Validate email format before sending
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }

        String username = profileService.generateUniqueUsername(email, null, null);

        // Clean metadata - remove null values and ensure all values are strings
        java.util.Map<String, Object> cleanMetadata = new java.util.HashMap<>();
        if (userMetadata != null) {
            cleanMetadata = userMetadata.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().toString()
                    ));
        }

        // Always include username in metadata
        cleanMetadata.put("username", username);

        // Build request body according to Supabase Auth API specification
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(), // Normalize email
                "password", password,
                "email_confirm", true, // Auto-confirm email for admin created users
                "user_metadata", cleanMetadata
        );
        
        log.debug("Creating user with request body: {}", requestBody);
        
        return supabaseAdminWebClient
                .post()
                .uri("/auth/v1/admin/users")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Supabase Auth API error: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(user -> log.info("Successfully created user with email: {}", email))
                .doOnError(error -> log.error("Failed to create user: {}", error.getMessage()));
    }
    
    /**
     * Create user with minimal data but including role in both user_metadata and app_metadata
     * This ensures the database trigger has the role information it needs
     */
    public Mono<JsonNode> createMinimalUser(String email, String password, String role) {
        return createMinimalUser(email, password, role, null, null);
    }

    /**
     * Create user with minimal data including optional first and last name
     * This ensures the database trigger has all the information it needs
     */
    public Mono<JsonNode> createMinimalUser(String email, String password, String role, String firstName, String lastName) {
        // Validate email format before sending
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }

        String username = profileService.generateUniqueUsername(email, firstName, lastName);

        // Normalize role to lowercase and validate
        // Database expects lowercase values: mentee, mentor, advisee, advisor, admin, company
        String userRole = role != null ? role.toLowerCase().trim() : "mentee";

        // Validate role is one of the allowed values
        if (!isValidRole(userRole)) {
            log.warn("Invalid role '{}' provided, defaulting to 'mentee'", userRole);
            userRole = "mentee";
        }

        // Build metadata with optional first and last name
        java.util.Map<String, Object> userMetadata = new java.util.HashMap<>();
        userMetadata.put("role", userRole);
        userMetadata.put("username", username);

        // Add first_name and last_name if provided
        if (firstName != null && !firstName.trim().isEmpty()) {
            userMetadata.put("firstName", firstName);
            userMetadata.put("first_name", firstName);
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            userMetadata.put("lastName", lastName);
            userMetadata.put("last_name", lastName);
        }

        java.util.Map<String, Object> appMetadata = new java.util.HashMap<>();
        appMetadata.put("role", userRole);
        appMetadata.put("username", username);

        // Minimal request body with role, username, and optional names in both metadata types
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password,
                "email_confirm", true,
                "user_metadata", userMetadata,
                "app_metadata", appMetadata
        );

        log.info("=== SUPABASE CREATE USER REQUEST ===");
        log.info("Endpoint: POST /auth/v1/admin/users");
        log.info("Request Body: {}", requestBody);

        return supabaseAdminWebClient
                .post()
                .uri("/auth/v1/admin/users")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> {
                        log.error("=== SUPABASE ERROR RESPONSE ===");
                        log.error("Status Code: {}", response.statusCode());
                        log.error("Status Text: {}", response.statusCode().toString());

                        return response.bodyToMono(String.class)
                            .doOnNext(body -> {
                                log.error("Error Response Body: {}", body);
                                log.error("=== END ERROR RESPONSE ===");
                            })
                            .map(body -> new RuntimeException("Supabase Auth API error: " + response.statusCode() + " - " + body));
                    }
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(user -> {
                    log.info("=== SUPABASE SUCCESS RESPONSE ===");
                    log.info("User created successfully: {}", user.toPrettyString());
                    log.info("User ID: {}", user.get("id"));
                    log.info("User Email: {}", user.get("email"));
                    log.info("=== END SUCCESS RESPONSE ===");
                })
                .doOnError(error -> {
                    log.error("=== SUPABASE REQUEST FAILED ===");
                    log.error("Error Type: {}", error.getClass().getName());
                    log.error("Error Message: {}", error.getMessage());
                    if (error.getCause() != null) {
                        log.error("Cause: {}", error.getCause().getMessage());
                    }
                    log.error("=== END REQUEST FAILED ===");
                });
    }
    
    /**
     * Create user without any automatic profile creation - bypass triggers completely
     */
    public Mono<JsonNode> createUserWithoutTriggers(String email, String password) {
        // This is an alternative approach - create user and handle profile creation manually
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password,
                "email_confirm", true
                // No metadata to minimize trigger issues
        );
        
        log.debug("Creating user without triggers with request body: {}", requestBody);
        
        return supabaseAdminWebClient
                .post()
                .uri("/auth/v1/admin/users")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Supabase Auth API error: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(user -> log.info("Successfully created user without triggers: {}", email))
                .doOnError(error -> log.error("Failed to create user without triggers: {}", error.getMessage()));
    }
    
    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }

    /**
     * Validate role is one of the allowed values
     * Database expects: mentee, mentor, advisee, advisor, admin, company, company_admin
     */
    private boolean isValidRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }

        return role.equals("mentee") ||
               role.equals("mentor") ||
               role.equals("advisee") ||
               role.equals("advisor") ||
               role.equals("admin") ||
               role.equals("company") ||
               role.equals("company_admin");
    }

    /**
     * Update user password (admin operation)
     */
    public Mono<JsonNode> updateUserPassword(String userId, String newPassword) {
        Map<String, Object> requestBody = Map.of("password", newPassword);
        
        return supabaseAdminWebClient
                .put()
                .uri("/auth/v1/admin/users/{userId}", userId)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.info("Successfully updated password for user: {}", userId))
                .doOnError(error -> log.error("Failed to update password: {}", error.getMessage()));
    }

    /**
     * Confirm an email verification token hash without redirecting the browser to Supabase.
     */
    public Mono<JsonNode> verifyEmailTokenHash(String tokenHash, String type) {
        if (tokenHash == null || tokenHash.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Token hash is required"));
        }

        String verificationType = type == null || type.trim().isEmpty()
                ? "signup"
                : type.trim().toLowerCase();

        Map<String, Object> requestBody = Map.of(
                "token_hash", tokenHash.trim(),
                "type", verificationType
        );

        return supabaseWebClient
                .post()
                .uri("/auth/v1/verify")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Email verification failed: "
                                        + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.info("Email verification completed via token hash"))
                .doOnError(error -> log.error("Failed to verify email token hash: {}", error.getMessage()));
    }

    /**
     * Sign in with email and password
     */
    public Mono<JsonNode> signInWithPassword(String email, String password) {
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password
        );

        log.info("=== SUPABASE SIGN-IN REQUEST ===");
        log.info("Endpoint: POST /auth/v1/token?grant_type=password");
        log.info("Email: {}", email.trim().toLowerCase());

        return supabaseWebClient
                .post()
                .uri("/auth/v1/token?grant_type=password")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> {
                        log.error("=== SUPABASE SIGN-IN ERROR ===");
                        log.error("Status Code: {}", response.statusCode());

                        return response.bodyToMono(String.class)
                            .doOnNext(body -> {
                                log.error("Error Response Body: {}", body);
                                log.error("=== END SIGN-IN ERROR ===");
                            })
                            .map(body -> new RuntimeException("Authentication failed: " + response.statusCode() + " - " + body));
                    }
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> {
                    log.info("=== SUPABASE SIGN-IN SUCCESS ===");
                    log.info("User signed in: {}", email);
                    log.info("Has access_token: {}", result.has("access_token"));
                    log.info("Has refresh_token: {}", result.has("refresh_token"));
                    log.info("Has user: {}", result.has("user"));
                    log.info("=== END SIGN-IN SUCCESS ===");
                })
                .doOnError(error -> {
                    log.error("=== SIGN-IN REQUEST FAILED ===");
                    log.error("Error: {}", error.getMessage());
                    log.error("=== END SIGN-IN FAILED ===");
                });
    }

    /**
     * Sign up new user with email and password
     */
    public Mono<JsonNode> signUpWithPassword(String email, String password, String role) {
        return signUpWithPassword(email, password, role, null, null);
    }

    /**
     * Sign up new user with email, password, role, and profile metadata.
     * This uses the public signup endpoint so Supabase can enforce email verification.
     */
    public Mono<JsonNode> signUpWithPassword(String email, String password, String role, String firstName, String lastName) {
        // Validate email format before sending
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }

        String username = profileService.generateUniqueUsername(email, firstName, lastName);

        String userRole = role != null ? role : "mentee";
        java.util.Map<String, Object> userMetadata = new java.util.HashMap<>();
        userMetadata.put("role", userRole);
        userMetadata.put("username", username);

        if (firstName != null && !firstName.trim().isEmpty()) {
            userMetadata.put("firstName", firstName);
            userMetadata.put("first_name", firstName);
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            userMetadata.put("lastName", lastName);
            userMetadata.put("last_name", lastName);
        }

        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password,
                "data", userMetadata // user_metadata goes in 'data' field for signup
        );
        
        log.debug("Attempting to sign up user with email: {}", email);
        
        return supabaseWebClient
                .post()
                .uri("/auth/v1/signup")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Signup failed: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.info("Successfully signed up user with email: {}", email))
                .doOnError(error -> log.error("Failed to sign up user: {}", error.getMessage()));
    }

    /**
     * Generate a Supabase signup confirmation link without sending Supabase's default email.
     * The caller is responsible for delivering the returned action_link.
     */
    public Mono<JsonNode> generateSignupConfirmationLink(String email,
                                                         String password,
                                                         String role,
                                                         String firstName,
                                                         String lastName,
                                                         String redirectTo) {
        return generateSignupConfirmationLink(email, password, role, firstName, lastName, null, redirectTo);
    }

    public Mono<JsonNode> generateSignupConfirmationLink(String email,
                                                         String password,
                                                         String role,
                                                         String firstName,
                                                         String lastName,
                                                         String phoneNumber,
                                                         String redirectTo) {
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }

        String username = profileService.generateUniqueUsername(email, firstName, lastName);
        String userRole = role != null ? role : "mentee";

        java.util.Map<String, Object> userMetadata = new java.util.HashMap<>();
        userMetadata.put("role", userRole);
        userMetadata.put("username", username);

        if (firstName != null && !firstName.trim().isEmpty()) {
            userMetadata.put("firstName", firstName);
            userMetadata.put("first_name", firstName);
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            userMetadata.put("lastName", lastName);
            userMetadata.put("last_name", lastName);
        }
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            String normalizedPhone = phoneNumber.trim();
            userMetadata.put("phoneNumber", normalizedPhone);
            userMetadata.put("phone_number", normalizedPhone);
            userMetadata.put("phone", normalizedPhone);
        }

        java.util.Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("type", "signup");
        requestBody.put("email", email.trim().toLowerCase());
        requestBody.put("password", password);
        requestBody.put("data", userMetadata);
        if (redirectTo != null && !redirectTo.isBlank()) {
            requestBody.put("redirect_to", redirectTo.trim());
        }

        log.debug("Generating signup confirmation link for email: {}", email);

        return supabaseAdminWebClient
                .post()
                .uri("/auth/v1/admin/generate_link")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> new RuntimeException("Signup confirmation link generation failed: "
                                        + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.info("Successfully generated signup confirmation link for email: {}", email))
                .doOnError(error -> log.error("Failed to generate signup confirmation link: {}", error.getMessage()));
    }

    /**
     * Refresh access token
     */
    public Mono<JsonNode> refreshToken(String refreshToken) {
        Map<String, Object> requestBody = Map.of("refresh_token", refreshToken);
        
        return supabaseWebClient
                .post()
                .uri("/auth/v1/token?grant_type=refresh_token")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Token refresh failed: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.debug("Successfully refreshed token"))
                .doOnError(error -> log.error("Failed to refresh token: {}", error.getMessage()));
    }

    /**
     * Sign out user (invalidate refresh token)
     */
    public Mono<Void> signOut(String accessToken) {
        return supabaseWebClient
                .post()
                .uri("/auth/v1/logout")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(result -> log.info("Successfully signed out user"))
                .doOnError(error -> log.error("Failed to sign out user: {}", error.getMessage()));
    }
}
