package com.prosper.prospermentor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.auth.UnifiedAuthSession;
import com.prosper.prospermentor.dto.auth.UnifiedSignupResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthSessionMapper {

    private static final String B2C_BASE_URL = "https://prospermentor.com";
    private static final String ENTERPRISE_BASE_URL = "https://enterprise.prospermentor.com";

    private final ObjectMapper objectMapper;

    public Map<String, Object> toLoginResponse(JsonNode authResponse,
                                               Map<String, Object> profile,
                                               Map<String, Object> freeTrial,
                                               boolean freeTrialRequested) {
        Map<String, Object> response = authResponse == null || authResponse.isNull()
                ? new LinkedHashMap<>()
                : objectMapper.convertValue(authResponse, new TypeReference<LinkedHashMap<String, Object>>() {});

        String accessToken = text(authResponse, "access_token");
        String refreshToken = text(authResponse, "refresh_token");
        JsonNode userNode = authResponse != null && authResponse.has("user") ? authResponse.get("user") : null;

        UnifiedAuthSession.UserPayload user = buildUser(userNode, profile);
        String normalizedRole = normalizeRole(value(profile, "role"));
        List<UnifiedAuthSession.MembershipPayload> memberships = buildMemberships(normalizedRole, profile);
        List<UnifiedAuthSession.EntitlementPayload> entitlements = buildEntitlements(freeTrial);
        UnifiedAuthSession.DestinationPayload destination = buildDestination(normalizedRole, freeTrialRequested);

        UnifiedAuthSession session = new UnifiedAuthSession(
                accessToken,
                refreshToken,
                user,
                profile,
                memberships,
                entitlements,
                null,
                destination
        );

        Map<String, Object> normalized = objectMapper.convertValue(session, new TypeReference<LinkedHashMap<String, Object>>() {});
        normalized.entrySet().removeIf(entry -> entry.getValue() == null);
        response.putAll(normalized);

        if (profile != null) {
            response.put("profile", profile);
        }
        if (freeTrial != null) {
            response.put("freeTrial", freeTrial);
        }

        return response;
    }

    public Map<String, Object> toRefreshResponse(JsonNode authResponse) {
        return toLoginResponse(authResponse, null, null, false);
    }

    public Map<String, Object> toSignupResponse(JsonNode signupResponse,
                                                Map<String, Object> profile,
                                                Map<String, Object> freeTrial,
                                                boolean freeTrialRequested,
                                                String message,
                                                String destinationUrl) {
        JsonNode userNode = signupResponse != null && signupResponse.has("user")
                ? signupResponse.get("user")
                : signupResponse;

        UnifiedAuthSession.UserPayload user = buildUser(userNode, profile);
        String normalizedRole = normalizeRole(value(profile, "role"));
        List<UnifiedAuthSession.MembershipPayload> memberships = buildMemberships(normalizedRole, profile);
        List<UnifiedAuthSession.EntitlementPayload> entitlements = buildEntitlements(freeTrial);
        UnifiedAuthSession.DestinationPayload destination = new UnifiedAuthSession.DestinationPayload(
                productForSignup(normalizedRole, freeTrialRequested),
                destinationUrl
        );

        UnifiedSignupResult result = new UnifiedSignupResult(
                "PENDING_EMAIL_VERIFICATION",
                true,
                message,
                user,
                profile,
                memberships,
                entitlements,
                destination,
                freeTrial
        );

        Map<String, Object> normalized = objectMapper.convertValue(result, new TypeReference<LinkedHashMap<String, Object>>() {});
        normalized.entrySet().removeIf(entry -> entry.getValue() == null);
        return normalized;
    }

    private UnifiedAuthSession.UserPayload buildUser(JsonNode userNode, Map<String, Object> profile) {
        String id = firstNonBlank(text(userNode, "id"), value(profile, "id"));
        String email = firstNonBlank(text(userNode, "email"), value(profile, "email"));
        String firstName = firstNonBlank(value(profile, "firstName"), value(profile, "first_name"), metadata(userNode, "first_name"), metadata(userNode, "firstName"));
        String lastName = firstNonBlank(value(profile, "lastName"), value(profile, "last_name"), metadata(userNode, "last_name"), metadata(userNode, "lastName"));
        String displayName = firstNonBlank(value(profile, "displayName"), joinName(firstName, lastName), email);
        String avatarUrl = firstNonBlank(value(profile, "avatarUrl"), value(profile, "avatar_url"), metadata(userNode, "avatar_url"));
        Boolean emailVerified = userNode != null && userNode.has("email_confirmed_at") && !userNode.get("email_confirmed_at").isNull();
        String provider = firstNonBlank(metadata(userNode, "provider"), "local");

        return new UnifiedAuthSession.UserPayload(id, email, firstName, lastName, displayName, avatarUrl, emailVerified, provider);
    }

    private List<UnifiedAuthSession.MembershipPayload> buildMemberships(String role, Map<String, Object> profile) {
        List<UnifiedAuthSession.MembershipPayload> memberships = new ArrayList<>();
        String companyId = firstNonBlank(value(profile, "companyId"), value(profile, "company_id"));

        if ("corporate_admin".equals(role) || "employee".equals(role)) {
            memberships.add(new UnifiedAuthSession.MembershipPayload("company", role, "active", companyId));
            return memberships;
        }

        memberships.add(new UnifiedAuthSession.MembershipPayload("b2c", role, "active", null));
        return memberships;
    }

    private List<UnifiedAuthSession.EntitlementPayload> buildEntitlements(Map<String, Object> freeTrial) {
        List<UnifiedAuthSession.EntitlementPayload> entitlements = new ArrayList<>();
        if (freeTrial == null || freeTrial.isEmpty()) {
            return entitlements;
        }

        boolean activated = Boolean.TRUE.equals(freeTrial.get("activated"));
        Integer remainingSessions = integerValue(freeTrial.get("remainingSessions"));
        Integer sessionDurationMinutes = integerValue(freeTrial.get("sessionDurationMinutes"));
        entitlements.add(new UnifiedAuthSession.EntitlementPayload(
                "free_trial",
                activated ? "active" : "unavailable",
                remainingSessions,
                sessionDurationMinutes
        ));
        return entitlements;
    }

    private UnifiedAuthSession.DestinationPayload buildDestination(String role, boolean freeTrialRequested) {
        if (freeTrialRequested) {
            return new UnifiedAuthSession.DestinationPayload("b2c", B2C_BASE_URL + "/mentors");
        }
        if ("corporate_admin".equals(role)) {
            return new UnifiedAuthSession.DestinationPayload("enterprise", ENTERPRISE_BASE_URL + "/app/admin");
        }
        if ("employee".equals(role)) {
            return new UnifiedAuthSession.DestinationPayload("enterprise", ENTERPRISE_BASE_URL + "/app/employee");
        }
        return new UnifiedAuthSession.DestinationPayload("b2c", B2C_BASE_URL + "/dashboard");
    }

    private String productForSignup(String role, boolean freeTrialRequested) {
        if (freeTrialRequested) {
            return "b2c";
        }
        if ("corporate_admin".equals(role) || "employee".equals(role)) {
            return "enterprise";
        }
        return "b2c";
    }

    private String normalizeRole(String rawRole) {
        String role = rawRole == null || rawRole.isBlank() ? "mentee" : rawRole.trim().toLowerCase();
        if (role.equals("company") || role.equals("company_admin")) {
            return "corporate_admin";
        }
        if (role.equals("advisee")) {
            return "mentee";
        }
        if (role.equals("advisor")) {
            return "mentor";
        }
        return role;
    }

    private String metadata(JsonNode userNode, String key) {
        if (userNode == null || !userNode.has("user_metadata")) {
            return null;
        }
        JsonNode metadata = userNode.get("user_metadata");
        return text(metadata, key);
    }

    private String text(JsonNode node, String key) {
        if (node == null || !node.has(key) || node.get(key).isNull()) {
            return null;
        }
        return node.get(key).asText();
    }

    private String value(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return String.valueOf(map.get(key));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String joinName(String firstName, String lastName) {
        String joined = String.join(" ",
                firstName == null ? "" : firstName.trim(),
                lastName == null ? "" : lastName.trim()
        ).trim();
        return joined.isBlank() ? null : joined;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return null;
    }
}
