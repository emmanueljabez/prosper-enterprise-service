package com.prosper.prospermentor.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedSignupResult(
        String status,
        Boolean emailVerificationRequired,
        String message,
        UnifiedAuthSession.UserPayload user,
        Map<String, Object> profile,
        List<UnifiedAuthSession.MembershipPayload> memberships,
        List<UnifiedAuthSession.EntitlementPayload> entitlements,
        UnifiedAuthSession.DestinationPayload defaultDestination,
        Map<String, Object> freeTrial
) {}
