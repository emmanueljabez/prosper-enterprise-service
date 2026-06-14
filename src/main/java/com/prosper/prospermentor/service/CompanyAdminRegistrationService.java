package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.prosper.prospermentor.dto.CompleteCompanySignupIntentRequest;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.CompanySignupIntent;
import com.prosper.prospermentor.service.notification.CompanyNotificationService;
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
public class CompanyAdminRegistrationService {

    private final CompanySignupIntentService companySignupIntentService;
    private final CompanyService companyService;
    private final CompanySubscriptionService companySubscriptionService;
    private final SupabaseAuthService supabaseAuthService;
    private final CompanyNotificationService companyNotificationService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public Mono<ResponseEntity<Object>> completeIntent(String token, CompleteCompanySignupIntentRequest request) {
        CompanySignupIntent intent = companySignupIntentService.requireActiveIntent(token);
        return completeFromRegistrationToken(intent.getCompanyRegistrationToken(), request)
                .map(response -> {
                    Object body = response.getBody();
                    if (body instanceof Map<?, ?> responseBody
                            && responseBody.get("user") instanceof Map<?, ?> userBody
                            && responseBody.get("profile") instanceof Map<?, ?> profileBody
                            && userBody.get("id") != null
                            && profileBody.get("id") != null) {
                        UUID linkedUserId = UUID.fromString(String.valueOf(userBody.get("id")));
                        UUID linkedProfileId = UUID.fromString(String.valueOf(profileBody.get("id")));
                        companySignupIntentService.markCompleted(token, linkedUserId, linkedProfileId);
                    }
                    return response;
                });
    }

    public Mono<ResponseEntity<Object>> completeFromRegistrationToken(String registrationToken,
                                                                      CompleteCompanySignupIntentRequest request) {
        String emailVerificationRedirectUrl = normalizeUrl(frontendUrl) + "/auth/login?email_verified=1";

        return supabaseAuthService.generateSignupConfirmationLink(
                        request.getEmail(),
                        request.getPassword(),
                        "company",
                        request.getFirstName(),
                        request.getLastName(),
                        emailVerificationRedirectUrl
                )
                .flatMap(signupResponse -> {
                    try {
                        JsonNode userNode = signupResponse.has("user") ? signupResponse.get("user") : signupResponse;
                        if (userNode == null || userNode.isNull() || !userNode.hasNonNull("id")) {
                            return Mono.just(ResponseEntity.internalServerError()
                                    .<Object>body(Map.of("error", "Signup provider did not return a user id")));
                        }

                        UUID userUuid = UUID.fromString(userNode.get("id").asText());
                        String email = userNode.hasNonNull("email") ? userNode.get("email").asText() : request.getEmail().trim().toLowerCase();

                        var registrationResponse = companyService.completeCompanyRegistrationWithProfile(
                                registrationToken,
                                userUuid,
                                email,
                                request.getFirstName(),
                                request.getLastName(),
                                request.getPhoneNumber(),
                                request.getDateOfBirth(),
                                false
                        );

                        if (!registrationResponse.isSuccess()) {
                            return Mono.just(ResponseEntity.badRequest()
                                    .<Object>body(Map.of("error", registrationResponse.getMessage())));
                        }

                        Map<String, Object> responseBody = new LinkedHashMap<>();
                        responseBody.put("user", toPublicUserPayload(userNode, email));
                        responseBody.put("profile", registrationResponse.getData().get("profile"));
                        responseBody.put("company", registrationResponse.getData().get("company"));
                        responseBody.put("emailVerificationRequired", true);
                        responseBody.put("message", "Company account created. Verify your email, then sign in to continue.");

                        String actionLink = signupResponse.hasNonNull("action_link")
                                ? signupResponse.get("action_link").asText()
                                : null;
                        if (actionLink == null || actionLink.isBlank()) {
                            return Mono.just(ResponseEntity.internalServerError()
                                    .<Object>body(Map.of("error", "Signup provider did not return a confirmation link")));
                        }

                        companyNotificationService.sendCompanyEmailConfirmation(
                                email,
                                request.getFirstName(),
                                resolveCompanyName(registrationResponse.getData().get("company")),
                                toFrontendConfirmationUrl(signupResponse, actionLink)
                        );
                        return Mono.just(ResponseEntity.ok((Object) responseBody));
                    } catch (Exception e) {
                        log.error("Error processing company registration user data: {}", e.getMessage(), e);
                        return Mono.just(ResponseEntity.internalServerError()
                                .<Object>body(Map.of("error", "Failed to process user data: " + e.getMessage())));
                    }
                })
                .onErrorResume(error -> {
                    String errorMessage = error.getMessage();
                    log.error("Company registration signup error: {}", errorMessage);

                    if (errorMessage != null && (errorMessage.contains("User already registered")
                            || errorMessage.contains("already exists")
                            || errorMessage.contains("email_exists")
                            || errorMessage.contains("422"))) {
                        return Mono.just(ResponseEntity.status(409)
                                .<Object>body(Map.of(
                                        "error", "An account with this email already exists. Please sign in to continue.",
                                        "errorCode", "USER_ALREADY_EXISTS"
                                )));
                    }

                    return Mono.just(ResponseEntity.internalServerError()
                            .<Object>body(Map.of("error", "Company registration service error. Please try again or contact support.")));
                });
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

    private String resolveCompanyName(Object companyPayload) {
        if (companyPayload instanceof Map<?, ?> companyMap) {
            Object companyName = companyMap.get("companyName");
            if (companyName != null && !String.valueOf(companyName).isBlank()) {
                return String.valueOf(companyName);
            }
        }
        return "your company";
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:3000";
        }
        return url.trim().replaceAll("/+$", "");
    }

    private String toFrontendConfirmationUrl(JsonNode signupResponse, String actionLink) {
        String tokenHash = resolveTokenHash(signupResponse, actionLink);
        String type = resolveVerificationType(signupResponse, actionLink);
        return normalizeUrl(frontendUrl)
                + "/auth/confirm-email?token_hash="
                + URLEncoder.encode(tokenHash, StandardCharsets.UTF_8)
                + "&type="
                + URLEncoder.encode(type, StandardCharsets.UTF_8);
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

    public Map<String, Object> resumePurchase(String token,
                                              UUID userId,
                                              Integer requestedSessionCount,
                                              String redirectSuccessUrl,
                                              String redirectCancelUrl) {
        CompanySignupIntent intent = companySignupIntentService.requirePurchasableIntent(token);
        if (intent.getTargetPlanId() == null || intent.getTargetSessionCount() == null) {
            throw new IllegalStateException("Signup intent does not contain a corporate pricing selection");
        }
        int sessionCount = requestedSessionCount != null ? requestedSessionCount : intent.getTargetSessionCount();
        if (requestedSessionCount != null && requestedSessionCount > 0 && requestedSessionCount != intent.getTargetSessionCount()) {
            intent = companySignupIntentService.updateTargetSessionCount(token, requestedSessionCount);
            sessionCount = intent.getTargetSessionCount();
        }
        return companySubscriptionService.createCompanySubscription(
                intent.getCompany().getId(),
                intent.getTargetPlanId(),
                sessionCount,
                BillingInterval.MONTHLY,
                userId,
                redirectSuccessUrl,
                redirectCancelUrl
        );
    }
}
