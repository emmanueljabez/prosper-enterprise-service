package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.community.CommunityRealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/realtime")
@RequiredArgsConstructor
public class CommunityRealtimeController {
    private final CommunityRealtimeService realtimeService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<?>> getEvents(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) Set<UUID> visiblePostIds,
            @RequestParam(defaultValue = "50") int limit
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Community realtime events retrieved successfully",
                realtimeService.getEvents(userId, since, visiblePostIds, limit)
        ));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<?> streamEvents(
            Authentication authentication,
            @RequestParam(required = false) Set<UUID> visiblePostIds
    ) {
        UUID userId = authenticatedUserId(authentication);
        if (userId == null) {
            return unauthorized();
        }

        SseEmitter emitter = realtimeService.openStream(userId, visiblePostIds);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
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
