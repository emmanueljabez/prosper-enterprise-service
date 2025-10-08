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
        
        // Clean metadata - remove null values and ensure all values are strings
        Map<String, Object> cleanMetadata = Map.of();
        if (userMetadata != null) {
            cleanMetadata = userMetadata.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().toString()
                    ));
        }
        
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
        // Validate email format before sending
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }
        
        // Include role in both user_metadata and app_metadata
        String userRole = role != null ? role : "mentee";
        Map<String, Object> userMetadata = Map.of("role", userRole);
        Map<String, Object> appMetadata = Map.of("role", userRole);
        
        // Minimal request body with role in both metadata types
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password,
                "email_confirm", true,
                "user_metadata", userMetadata,
                "app_metadata", appMetadata
        );
        
        log.debug("Creating minimal user with request body: {}", requestBody);
        
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
                .doOnSuccess(user -> log.info("Successfully created minimal user with email: {}", email))
                .doOnError(error -> log.error("Failed to create minimal user: {}", error.getMessage()));
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
     * Sign in with email and password
     */
    public Mono<JsonNode> signInWithPassword(String email, String password) {
        Map<String, Object> requestBody = Map.of(
                "email", email.trim().toLowerCase(),
                "password", password
        );
        
        log.debug("Attempting to sign in user with email: {}", email);
        
        return supabaseWebClient
                .post()
                .uri("/auth/v1/token?grant_type=password")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Authentication failed: " + response.statusCode() + " - " + body))
                )
                .bodyToMono(JsonNode.class)
                .doOnSuccess(result -> log.info("Successfully signed in user with email: {}", email))
                .doOnError(error -> log.error("Failed to sign in user: {}", error.getMessage()));
    }

    /**
     * Sign up new user with email and password
     */
    public Mono<JsonNode> signUpWithPassword(String email, String password, String role) {
        // Validate email format before sending
        if (!isValidEmail(email)) {
            return Mono.error(new IllegalArgumentException("Invalid email format: " + email));
        }
        
        String userRole = role != null ? role : "mentee";
        Map<String, Object> userMetadata = Map.of("role", userRole);
        
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

