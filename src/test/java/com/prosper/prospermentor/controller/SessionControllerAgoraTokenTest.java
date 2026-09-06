package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.ApiResponse;
import com.prosper.prospermentor.controller.dto.SessionDtos.AgoraJoinTokenResponseDto;
import com.prosper.prospermentor.repository.SessionOutcomeRepository;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.NautixWhatsAppService;
import com.prosper.prospermentor.service.SessionBookingService;
import com.prosper.prospermentor.service.meeting.AgoraSessionAccessService;
import com.prosper.prospermentor.service.meeting.AgoraTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionControllerAgoraTokenTest {

    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROFILE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final SessionBookingService sessionBookingService = mock(SessionBookingService.class);
    private final NautixWhatsAppService nautixWhatsAppService = mock(NautixWhatsAppService.class);
    private final SessionOutcomeRepository sessionOutcomeRepository = mock(SessionOutcomeRepository.class);
    private final AgoraSessionAccessService agoraSessionAccessService = mock(AgoraSessionAccessService.class);
    private final SessionController controller = new SessionController(
            sessionBookingService,
            nautixWhatsAppService,
            sessionOutcomeRepository,
            agoraSessionAccessService
    );

    @Test
    void createAgoraToken_shouldRequireAuthentication() {
        ResponseEntity<ApiResponse<AgoraJoinTokenResponseDto>> response = controller.createAgoraToken(SESSION_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
    }

    @Test
    void createAgoraToken_shouldReturnTokenForAuthenticatedParticipant() {
        AgoraTokenService.AgoraJoinToken token = new AgoraTokenService.AgoraJoinToken(
                "app-id",
                "pm-session-" + SESSION_ID,
                PROFILE_ID.toString(),
                "rtc-token",
                Instant.parse("2026-09-06T13:00:00Z")
        );

        when(agoraSessionAccessService.createJoinToken(SESSION_ID, PROFILE_ID)).thenReturn(token);

        ResponseEntity<ApiResponse<AgoraJoinTokenResponseDto>> response = controller.createAgoraToken(
                SESSION_ID,
                authentication(PROFILE_ID)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getAppId()).isEqualTo("app-id");
        assertThat(response.getBody().getData().getChannelName()).isEqualTo("pm-session-" + SESSION_ID);
        assertThat(response.getBody().getData().getUid()).isEqualTo(PROFILE_ID.toString());
        assertThat(response.getBody().getData().getToken()).isEqualTo("rtc-token");
        verify(agoraSessionAccessService).createJoinToken(SESSION_ID, PROFILE_ID);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID profileId) {
        return new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(profileId.toString(), "member@example.com", "MENTEE"),
                null
        );
    }
}
