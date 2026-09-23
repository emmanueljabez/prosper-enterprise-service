package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.controller.AuthController;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.notification.MenteeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSignupService {

    private final SupabaseAuthService supabaseAuthService;
    private final ProfileService profileService;
    private final SubscriptionService subscriptionService;
    private final MenteeNotificationService menteeNotificationService;
    private final AuthSessionMapper authSessionMapper;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public Mono<ResponseEntity<Object>> signup(AuthController.SignupRequest signupRequest) {
        if (signupRequest.getEmail() == null || signupRequest.getPassword() == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Email and password are required", "errorCode", "MISSING_CREDENTIALS")));
        }

        if (signupRequest.getPassword().length() < 6) {
            return Mono.just(ResponseEntity.badRequest()
                    .<Object>body(Map.of("error", "Password must be at least 6 characters long", "errorCode", "WEAK_PASSWORD")));
        }

        String role = normalizeRole(signupRequest.getRole());
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
                .flatMap(authResponse -> buildSignupResponse(signupRequest, authResponse, role, freeTrialRequested))
                .onErrorResume(error -> Mono.just(mapSignupError(error)));
    }

    private Mono<ResponseEntity<Object>> buildSignupResponse(AuthController.SignupRequest request,
                                                            JsonNode authResponse,
                                                            String role,
                                                            boolean freeTrialRequested) {
        try {
            JsonNode userNode = authResponse.has("user") ? authResponse.get("user") : authResponse;
            if (userNode == null || userNode.isNull() || !userNode.hasNonNull("id")) {
                return Mono.just(ResponseEntity.internalServerError()
                        .<Object>body(Map.of("error", "Signup provider did not return a user id", "errorCode", "SIGNUP_PROVIDER_ERROR")));
            }

            String userId = userNode.get("id").asText();
            String email = userNode.hasNonNull("email")
                    ? userNode.get("email").asText()
                    : request.getEmail().trim().toLowerCase();
            UUID userUuid = UUID.fromString(userId);

            var profile = profileService.createProfileWithDetails(
                    userUuid,
                    email,
                    role,
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPhoneNumber(),
                    request.getDateOfBirth()
            );

            Map<String, Object> freeTrial = freeTrialRequested ? activateFreeTrial(userUuid) : null;
            String actionLink = authResponse.hasNonNull("action_link") ? authResponse.get("action_link").asText() : null;
            if (actionLink == null || actionLink.isBlank()) {
                return Mono.just(ResponseEntity.internalServerError()
                        .<Object>body(Map.of("error", "Signup provider did not return a confirmation link", "errorCode", "SIGNUP_PROVIDER_ERROR")));
            }

            menteeNotificationService.sendMenteeEmailConfirmation(
                    email,
                    request.getFirstName(),
                    freeTrialRequested,
                    toFrontendConfirmationUrl(authResponse, actionLink, freeTrialRequested, role)
            );

            Map<String, Object> response = authSessionMapper.toSignupResponse(
                    authResponse,
                    profile.orElse(null),
                    freeTrial,
                    freeTrialRequested,
                    signupMessage(freeTrialRequested),
                    emailVerificationDestination(email, freeTrialRequested, role)
            );

            log.info("Signup created pending email verification account for: {}", email);
            return Mono.just(ResponseEntity.ok((Object) response));
        } catch (Exception e) {
            log.error("Error processing signup response: {}", e.getMessage(), e);
            return Mono.just(ResponseEntity.internalServerError()
                    .<Object>body(Map.of("error", "Failed to process signup. Please try again or contact support.", "errorCode", "SIGNUP_PROCESSING_ERROR")));
        }
    }

    private ResponseEntity<Object> mapSignupError(Throwable error) {
        String errorMessage = error.getMessage() == null ? "" : error.getMessage();
        log.error("Signup error: {}", errorMessage);

        if (errorMessage.contains("User already registered")
                || errorMessage.contains("already exists")
                || errorMessage.contains("email_exists")
                || errorMessage.contains("422")
                || errorMessage.contains("Database error saving new user")
                || errorMessage.contains("unexpected_failure")) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "User already exists with this email", "errorCode", "EMAIL_ALREADY_EXISTS"));
        }
        if (errorMessage.contains("Invalid email format")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid email format", "errorCode", "INVALID_EMAIL"));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Signup service error. Please try again or contact support.", "errorCode", "SIGNUP_SERVICE_ERROR"));
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

    private String emailVerificationDestination(String email, boolean freeTrialRequested, String role) {
        StringBuilder url = new StringBuilder(normalizeBaseUrl(frontendUrl))
                .append("/auth/email-verification?email=")
                .append(URLEncoder.encode(email, StandardCharsets.UTF_8));
        if (freeTrialRequested) {
            url.append("&audience=mentee&trial=1&product=FREE_TRIAL");
        } else if (role != null && !role.isBlank()) {
            url.append("&audience=").append(URLEncoder.encode(role.trim().toLowerCase(), StandardCharsets.UTF_8));
        }
        return url.toString();
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
            url.append("&audience=").append(URLEncoder.encode(role.trim().toLowerCase(), StandardCharsets.UTF_8));
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

    private boolean isFreeTrialRequested(AuthController.SignupRequest request) {
        return request != null && (Boolean.TRUE.equals(request.getTrial())
                || "FREE_TRIAL".equalsIgnoreCase(String.valueOf(request.getProduct()).trim()));
    }

    private String normalizeRole(String rawRole) {
        String role = rawRole == null || rawRole.isBlank() ? "mentee" : rawRole.trim().toLowerCase();
        if ("company".equals(role) || "company_admin".equals(role)) {
            return "corporate_admin";
        }
        if ("advisee".equals(role)) {
            return "mentee";
        }
        if ("advisor".equals(role)) {
            return "mentor";
        }
        return role;
    }

    private String signupMessage(boolean freeTrialRequested) {
        return freeTrialRequested
                ? "Account created. Verify your email, then sign in to book your free trial."
                : "Account created. Verify your email, then sign in to continue.";
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
}
