package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.badge.BadgeAwardDto;
import com.prosper.prospermentor.dto.badge.BadgeVisibilityRequest;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProfileBadgeController {

    private final BadgeService badgeService;

    @GetMapping("/me/badges")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyBadges(Authentication authentication) {
        try {
            UUID profileId = requireUser(authentication).getUserIdAsUuid();
            List<BadgeAwardDto> badges = badgeService.getMyBadges(profileId);
            return ResponseEntity.ok(ApiResponse.success("Badges retrieved", badgeListData(badges)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/me/badges/{awardId}/primary")
    public ResponseEntity<ApiResponse<BadgeAwardDto>> makePrimary(@PathVariable UUID awardId,
                                                                  Authentication authentication) {
        try {
            UUID profileId = requireUser(authentication).getUserIdAsUuid();
            return ResponseEntity.ok(ApiResponse.success("Primary badge updated", badgeService.makePrimary(profileId, awardId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/me/badges/{awardId}/visibility")
    public ResponseEntity<ApiResponse<BadgeAwardDto>> updateVisibility(@PathVariable UUID awardId,
                                                                       @RequestBody BadgeVisibilityRequest request,
                                                                       Authentication authentication) {
        try {
            UUID profileId = requireUser(authentication).getUserIdAsUuid();
            return ResponseEntity.ok(ApiResponse.success(
                    "Badge visibility updated",
                    badgeService.updateVisibility(profileId, awardId, request.visibility())
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/profiles/{profileId}/badges")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicBadges(@PathVariable UUID profileId) {
        List<BadgeAwardDto> badges = badgeService.getPublicBadges(profileId);
        return ResponseEntity.ok(ApiResponse.success("Public badges retrieved", badgeListData(badges)));
    }

    private SupabaseUserDetails requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication is required");
        }
        if (userDetails.getUserIdAsUuid() == null) {
            throw new SecurityException("Authenticated user id is invalid");
        }
        return userDetails;
    }

    private Map<String, Object> badgeListData(List<BadgeAwardDto> badges) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("badges", badges);
        data.put("count", badges.size());
        return data;
    }
}
