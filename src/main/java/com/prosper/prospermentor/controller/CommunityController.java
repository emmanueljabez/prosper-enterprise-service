package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.community.CommunityReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityReadService communityReadService;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<?>> getFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "latest") String mode,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Community feed retrieved successfully",
                communityReadService.getFeed(userId, mode, limit)
        ));
    }

    @GetMapping("/recommendations/people")
    public ResponseEntity<ApiResponse<?>> getRecommendedPeople(
            Authentication authentication,
            @RequestParam(defaultValue = "12") int limit
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "People recommendations retrieved successfully",
                communityReadService.getRecommendedPeople(userId, limit)
        ));
    }

    @GetMapping("/networks")
    public ResponseEntity<ApiResponse<?>> getNetworkOverview(Authentication authentication) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Network overview retrieved successfully",
                communityReadService.getNetworkOverview(userId)
        ));
    }

    private UUID authenticatedUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        String userId = null;

        if (principal instanceof SupabaseUserDetails userDetails) {
            userId = userDetails.getUserId();
        } else if (principal instanceof SupabaseUserPrincipal supabaseUserPrincipal) {
            userId = supabaseUserPrincipal.getUserId();
        }

        if (userId == null || userId.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ResponseEntity<ApiResponse<?>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required"));
    }
}
