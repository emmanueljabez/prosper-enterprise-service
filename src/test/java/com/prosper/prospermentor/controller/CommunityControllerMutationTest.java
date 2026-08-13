package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesDto;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostMutationResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionResponse;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.security.SupabaseUserPrincipal;
import com.prosper.prospermentor.service.community.CommunityMutationService;
import com.prosper.prospermentor.service.community.CommunityReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityControllerMutationTest {
    private final CommunityReadService readService = mock(CommunityReadService.class);
    private final CommunityMutationService mutationService = mock(CommunityMutationService.class);
    private final CommunityController controller = new CommunityController(readService, mutationService);

    @Test
    void createPostUsesAuthenticatedPrincipalAndReturnsCreated() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPostRequest request = postRequest("What should I ask in a first mentor session?");
        CommunityPostMutationResponse response = new CommunityPostMutationResponse(
                postId,
                viewerId,
                null,
                request.content(),
                "PUBLIC",
                "ACTIVE",
                "APPROVED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0,
                0,
                0,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(mutationService.createPost(eq(viewerId), eq(request))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.createPost(authentication(viewerId), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).createPost(viewerId, request);
    }

    @Test
    void reactToPostUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityReactionRequest request = new CommunityReactionRequest("like");
        CommunityReactionResponse response = new CommunityReactionResponse(postId, "LIKE", true, 1);
        when(mutationService.reactToPost(eq(viewerId), eq(postId), eq(request))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.reactToPost(authentication(viewerId), postId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
    }

    @Test
    void getNotificationPreferencesUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        CommunityNotificationPreferencesDto response = new CommunityNotificationPreferencesDto(
                viewerId,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                "DAILY",
                null,
                null,
                OffsetDateTime.now()
        );
        when(mutationService.getNotificationPreferences(viewerId)).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.getNotificationPreferences(authentication(viewerId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
    }

    @Test
    void createPostMapsIllegalArgumentToBadRequest() {
        UUID viewerId = UUID.randomUUID();
        CommunityPostRequest request = postRequest(" ");
        when(mutationService.createPost(eq(viewerId), eq(request)))
                .thenThrow(new IllegalArgumentException("Post content is required"));

        ResponseEntity<ApiResponse<?>> result = controller.createPost(authentication(viewerId), request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("Post content is required");
    }

    @Test
    void createPostReturnsUnauthorizedWhenAuthenticationIsMissing() {
        CommunityPostRequest request = postRequest("Question");

        ResponseEntity<ApiResponse<?>> result = controller.createPost(null, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(mutationService, never()).createPost(any(), any());
    }

    private Authentication authentication(UUID viewerId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new SupabaseUserPrincipal(
                viewerId.toString(),
                "member@example.com",
                "MENTEE"
        ));
        return authentication;
    }

    private CommunityPostRequest postRequest(String content) {
        return new CommunityPostRequest(
                null,
                content,
                "PUBLIC",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
