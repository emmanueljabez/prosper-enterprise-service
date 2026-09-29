package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.badge.BadgeAffiliationRuleDto;
import com.prosper.prospermentor.dto.badge.BadgeAffiliationRuleRequest;
import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeDto;
import com.prosper.prospermentor.dto.badge.BadgeTypeUpsertRequest;
import com.prosper.prospermentor.dto.badge.ManualBadgeGrantRequest;
import com.prosper.prospermentor.dto.badge.RevokeBadgeRequest;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/badges")
@RequiredArgsConstructor
@Slf4j
public class BadgeAdminController {

    private final BadgeService badgeService;

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTypes(Authentication authentication) {
        try {
            requireProsperAdmin(authentication);
            List<BadgeTypeDto> types = badgeService.getBadgeTypes();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("types", types);
            data.put("count", types.size());
            return ResponseEntity.ok(ApiResponse.success("Badge types retrieved", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/types")
    public ResponseEntity<ApiResponse<BadgeTypeDto>> createType(@RequestBody BadgeTypeUpsertRequest request,
                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge type created",
                    badgeService.createBadgeType(request, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/types/{badgeTypeId}")
    public ResponseEntity<ApiResponse<BadgeTypeDto>> updateType(@PathVariable UUID badgeTypeId,
                                                                @RequestBody BadgeTypeUpsertRequest request,
                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge type updated",
                    badgeService.updateBadgeType(badgeTypeId, request, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/types/{badgeTypeId}/retire")
    public ResponseEntity<ApiResponse<BadgeTypeDto>> retireType(@PathVariable UUID badgeTypeId,
                                                                Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge type retired",
                    badgeService.retireBadgeType(badgeTypeId, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/affiliation-rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listAffiliationRules(Authentication authentication) {
        try {
            requireProsperAdmin(authentication);
            List<BadgeAffiliationRuleDto> rules = badgeService.getAffiliationRules();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rules", rules);
            data.put("count", rules.size());
            return ResponseEntity.ok(ApiResponse.success("Badge affiliation rules retrieved", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/affiliation-rules")
    public ResponseEntity<ApiResponse<BadgeAffiliationRuleDto>> createAffiliationRule(@RequestBody BadgeAffiliationRuleRequest request,
                                                                                     Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge affiliation rule created",
                    badgeService.createAffiliationRule(request, userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/profiles/{profileId}/awards")
    public ResponseEntity<ApiResponse<BadgeAwardDto>> grantBadge(@PathVariable UUID profileId,
                                                                 @RequestBody ManualBadgeGrantRequest request,
                                                                 Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge granted",
                    badgeService.grantManualBadge(profileId, request.badgeTypeId(), request.note(), userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/awards/{awardId}/revoke")
    public ResponseEntity<ApiResponse<BadgeAwardDto>> revokeBadge(@PathVariable UUID awardId,
                                                                  @RequestBody RevokeBadgeRequest request,
                                                                  Authentication authentication) {
        try {
            SupabaseUserDetails userDetails = requireProsperAdmin(authentication);
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge revoked",
                    badgeService.revokeAward(awardId, request.note(), userDetails.getUserIdAsUuid())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    private SupabaseUserDetails requireProsperAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)
                || !userDetails.isAdmin()) {
            throw new SecurityException("Prosper admin access is required");
        }
        if (userDetails.getUserIdAsUuid() == null) {
            throw new SecurityException("Authenticated user id is invalid");
        }
        return userDetails;
    }
}
