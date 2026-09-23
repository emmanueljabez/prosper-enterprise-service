package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthSessionMapper mapper = new AuthSessionMapper(objectMapper);

    @Test
    void toLoginResponse_shouldReturnNormalizedFieldsAndKeepLegacyTokens() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        JsonNode authResponse = objectMapper.readTree("""
                {
                  "access_token": "legacy-access",
                  "refresh_token": "legacy-refresh",
                  "user": {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "email": "hr@example.com",
                    "user_metadata": {
                      "first_name": "Grace",
                      "last_name": "Admin"
                    }
                  }
                }
                """);
        Map<String, Object> profile = Map.of(
                "id", userId,
                "email", "hr@example.com",
                "firstName", "Grace",
                "lastName", "Admin",
                "role", "company",
                "companyId", "22222222-2222-2222-2222-222222222222"
        );

        Map<String, Object> response = mapper.toLoginResponse(authResponse, profile, null, false);

        assertThat(response)
                .containsEntry("access_token", "legacy-access")
                .containsEntry("refresh_token", "legacy-refresh")
                .containsEntry("accessToken", "legacy-access")
                .containsEntry("refreshToken", "legacy-refresh")
                .containsKeys("user", "profile", "memberships", "entitlements", "defaultDestination");

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.get("user");
        assertThat(user)
                .containsEntry("id", userId.toString())
                .containsEntry("email", "hr@example.com")
                .containsEntry("firstName", "Grace")
                .containsEntry("lastName", "Admin")
                .containsEntry("displayName", "Grace Admin");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memberships = (List<Map<String, Object>>) response.get("memberships");
        assertThat(memberships).containsExactly(Map.of(
                "type", "company",
                "role", "corporate_admin",
                "status", "active",
                "companyId", "22222222-2222-2222-2222-222222222222"
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) response.get("defaultDestination");
        assertThat(destination)
                .containsEntry("product", "enterprise")
                .containsEntry("url", "https://enterprise.prospermentor.com/app/admin");
    }

    @Test
    void toLoginResponse_shouldExposeFreeTrialEntitlementAndB2cDestination() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        JsonNode authResponse = objectMapper.readTree("""
                {
                  "access_token": "trial-access",
                  "refresh_token": "trial-refresh",
                  "user": {
                    "id": "33333333-3333-3333-3333-333333333333",
                    "email": "mentee@example.com"
                  }
                }
                """);
        Map<String, Object> profile = Map.of(
                "id", userId,
                "email", "mentee@example.com",
                "firstName", "Mentee",
                "lastName", "User",
                "role", "mentee"
        );
        Map<String, Object> freeTrial = Map.of(
                "requested", true,
                "activated", true,
                "remainingSessions", 1,
                "sessionDurationMinutes", 30
        );

        Map<String, Object> response = mapper.toLoginResponse(authResponse, profile, freeTrial, true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entitlements = (List<Map<String, Object>>) response.get("entitlements");
        assertThat(entitlements).containsExactly(Map.of(
                "type", "free_trial",
                "status", "active",
                "remainingSessions", 1,
                "sessionDurationMinutes", 30
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) response.get("defaultDestination");
        assertThat(destination)
                .containsEntry("product", "b2c")
                .containsEntry("url", "https://prospermentor.com/mentors");
    }

    @Test
    void toSignupResponse_shouldReturnPendingVerificationEnvelope() throws Exception {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        JsonNode signupResponse = objectMapper.readTree("""
                {
                  "user": {
                    "id": "44444444-4444-4444-4444-444444444444",
                    "email": "mentee@example.com",
                    "user_metadata": {
                      "first_name": "Mentee",
                      "last_name": "Signup"
                    }
                  }
                }
                """);
        Map<String, Object> profile = Map.of(
                "id", userId,
                "email", "mentee@example.com",
                "firstName", "Mentee",
                "lastName", "Signup",
                "role", "mentee"
        );
        Map<String, Object> freeTrial = Map.of(
                "requested", true,
                "activated", true,
                "remainingSessions", 1,
                "sessionDurationMinutes", 30
        );

        Map<String, Object> response = mapper.toSignupResponse(
                signupResponse,
                profile,
                freeTrial,
                true,
                "Account created. Verify your email, then sign in to continue.",
                "https://enterprise.prospermentor.com/auth/email-verification?email=mentee%40example.com"
        );

        assertThat(response)
                .containsEntry("status", "PENDING_EMAIL_VERIFICATION")
                .containsEntry("emailVerificationRequired", true)
                .containsEntry("message", "Account created. Verify your email, then sign in to continue.")
                .containsKeys("user", "profile", "memberships", "entitlements", "defaultDestination", "freeTrial");
        assertThat(response).doesNotContainKeys("accessToken", "refreshToken", "access_token", "refresh_token");

        @SuppressWarnings("unchecked")
        Map<String, Object> destination = (Map<String, Object>) response.get("defaultDestination");
        assertThat(destination)
                .containsEntry("product", "b2c")
                .containsEntry("url", "https://enterprise.prospermentor.com/auth/email-verification?email=mentee%40example.com");
    }
}
