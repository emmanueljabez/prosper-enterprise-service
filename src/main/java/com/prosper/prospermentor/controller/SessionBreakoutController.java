package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.ApiResponse;
import com.prosper.prospermentor.controller.dto.SessionDtos;
import com.prosper.prospermentor.dto.SessionBreakoutDtos;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.meeting.AgoraTokenService;
import com.prosper.prospermentor.service.meeting.SessionBreakoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/breakouts")
@Tag(name = "Session Breakouts", description = "Agora breakout room management for corporate group sessions")
public class SessionBreakoutController {

    private final SessionBreakoutService sessionBreakoutService;

    public SessionBreakoutController(SessionBreakoutService sessionBreakoutService) {
        this.sessionBreakoutService = sessionBreakoutService;
    }

    @GetMapping("/state")
    @Operation(summary = "Get breakout room state")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> getState(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        UUID profileId = authenticatedUserId(authentication);
        if (profileId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(sessionBreakoutService.getState(sessionId, profileId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/rooms")
    @Operation(summary = "Create breakout rooms")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> createRooms(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) SessionBreakoutDtos.CreateBreakoutRoomsRequest request,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.createRooms(sessionId, profileId, request));
    }

    @PostMapping("/auto-assign")
    @Operation(summary = "Auto assign participants to breakout rooms")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> autoAssign(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.autoAssign(sessionId, profileId));
    }

    @PatchMapping("/participants/{profileId}")
    @Operation(summary = "Move a participant to a breakout room")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> moveParticipant(
            @PathVariable UUID sessionId,
            @PathVariable UUID profileId,
            @RequestBody SessionBreakoutDtos.MoveBreakoutParticipantRequest request,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, requesterProfileId ->
                sessionBreakoutService.moveParticipant(sessionId, requesterProfileId, profileId, request));
    }

    @PostMapping("/open")
    @Operation(summary = "Open breakout rooms")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> openBreakouts(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.openBreakouts(sessionId, profileId));
    }

    @PostMapping("/rooms/{roomId}/token")
    @Operation(summary = "Create an Agora token for a breakout room")
    public ResponseEntity<ApiResponse<SessionDtos.AgoraJoinTokenResponseDto>> createRoomToken(
            @PathVariable UUID sessionId,
            @PathVariable UUID roomId,
            Authentication authentication
    ) {
        UUID profileId = authenticatedUserId(authentication);
        if (profileId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }

        try {
            AgoraTokenService.AgoraJoinToken token = sessionBreakoutService.createRoomToken(sessionId, roomId, profileId);
            SessionDtos.AgoraJoinTokenResponseDto response = SessionDtos.AgoraJoinTokenResponseDto.builder()
                    .appId(token.appId())
                    .channelName(token.channelName())
                    .uid(token.uid())
                    .token(token.token())
                    .expiresAt(token.expiresAt())
                    .build();
            return ResponseEntity.ok(ApiResponse.success(response, "Agora breakout token generated successfully"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{roomId}/join")
    @Operation(summary = "Mark a participant joined to a breakout room")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> markJoined(
            @PathVariable UUID sessionId,
            @PathVariable UUID roomId,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.markJoined(sessionId, roomId, profileId));
    }

    @PostMapping("/return-main")
    @Operation(summary = "Return a participant to the main session")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> returnToMain(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.returnToMain(sessionId, profileId));
    }

    @PostMapping("/close")
    @Operation(summary = "Close breakout rooms")
    public ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> closeBreakouts(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        return handleStateResponse(authentication, profileId -> sessionBreakoutService.closeBreakouts(sessionId, profileId));
    }

    private ResponseEntity<ApiResponse<SessionBreakoutDtos.SessionBreakoutStateDto>> handleStateResponse(
            Authentication authentication,
            BreakoutStateOperation operation
    ) {
        UUID profileId = authenticatedUserId(authentication);
        if (profileId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(operation.execute(profileId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
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
        } else if (principal instanceof String stringPrincipal) {
            userId = stringPrincipal;
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

    private interface BreakoutStateOperation {
        SessionBreakoutDtos.SessionBreakoutStateDto execute(UUID profileId);
    }
}
