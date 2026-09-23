package com.prosper.prospermentor.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedAuthSession(
        String accessToken,
        String refreshToken,
        UserPayload user,
        Map<String, Object> profile,
        List<MembershipPayload> memberships,
        List<EntitlementPayload> entitlements,
        String nextAction,
        DestinationPayload defaultDestination
) {
    public record UserPayload(
            String id,
            String email,
            String firstName,
            String lastName,
            String displayName,
            String avatarUrl,
            Boolean emailVerified,
            String provider
    ) {}

    public record MembershipPayload(
            String type,
            String role,
            String status,
            String companyId
    ) {}

    public record EntitlementPayload(
            String type,
            String status,
            Integer remainingSessions,
            Integer sessionDurationMinutes
    ) {}

    public record DestinationPayload(
            String product,
            String url
    ) {}
}
