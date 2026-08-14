package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityRealtimeEventsResponse;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.community.CommunityRealtimeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityRealtimeControllerTest {
    private final CommunityRealtimeService realtimeService = mock(CommunityRealtimeService.class);
    private final CommunityRealtimeController controller = new CommunityRealtimeController(realtimeService);

    @Test
    void eventsRequireAuthentication() {
        ResponseEntity<ApiResponse<?>> response = controller.getEvents(null, null, Set.of(), 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void eventsUseAuthenticatedPrincipalAndVisiblePostScope() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        OffsetDateTime since = OffsetDateTime.parse("2026-08-14T09:15:00Z");
        CommunityRealtimeEventsResponse serviceResponse = new CommunityRealtimeEventsResponse(
                List.of(new CommunityRealtimeEventItem(
                        UUID.randomUUID(),
                        "community.post.comment.created",
                        "COMMUNITY_COMMENT_CREATED",
                        "COMMENT",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        Map.of("postId", postId.toString()),
                        since.plusSeconds(1)
                )),
                25
        );
        when(realtimeService.getEvents(eq(viewerId), eq(since), eq(Set.of(postId)), eq(25)))
                .thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<?>> response = controller.getEvents(
                authentication(viewerId),
                since,
                Set.of(postId),
                25
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        verify(realtimeService).getEvents(viewerId, since, Set.of(postId), 25);
    }

    @Test
    void streamUsesAuthenticatedPrincipalAndVisiblePostScope() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();
        when(realtimeService.openStream(eq(viewerId), eq(Set.of(postId)))).thenReturn(emitter);

        ResponseEntity<?> response = controller.streamEvents(authentication(viewerId), Set.of(postId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(emitter);
        verify(realtimeService).openStream(viewerId, Set.of(postId));
    }

    private UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new SupabaseUserDetails(userId.toString(), "member@example.com", "MENTEE"),
                null
        );
    }
}
