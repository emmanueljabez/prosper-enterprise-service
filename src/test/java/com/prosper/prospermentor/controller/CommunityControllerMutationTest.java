package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentReactionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionStatusRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFollowResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityMyPostsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesDto;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostMutationResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileSummary;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySavedPostsResponse;
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
    void getPostUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPostItem response = postItem(postId, viewerId);
        when(readService.getPost(eq(viewerId), eq(postId))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.getPost(authentication(viewerId), postId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(readService).getPost(viewerId, postId);
    }

    @Test
    void getSavedPostsUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        CommunitySavedPostsResponse response = new CommunitySavedPostsResponse(List.of(), 20);
        when(readService.getSavedPosts(eq(viewerId), eq(20))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.getSavedPosts(authentication(viewerId), 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(readService).getSavedPosts(viewerId, 20);
    }

    @Test
    void getMyPostsUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        CommunityMyPostsResponse response = new CommunityMyPostsResponse(List.of(), 20);
        when(readService.getMyPosts(eq(viewerId), eq(20))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.getMyPosts(authentication(viewerId), 20);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(readService).getMyPosts(viewerId, 20);
    }

    @Test
    void setPostHiddenUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPostHiddenRequest request = new CommunityPostHiddenRequest(true);
        CommunityPostHiddenResponse response = new CommunityPostHiddenResponse(postId, true);
        when(mutationService.setPostHidden(eq(viewerId), eq(postId), eq(request))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.setPostHidden(authentication(viewerId), postId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).setPostHidden(eq(viewerId), eq(postId), any(CommunityPostHiddenRequest.class));
    }

    @Test
    void reactToCommentUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        CommunityReactionRequest request = new CommunityReactionRequest("like");
        CommunityCommentReactionResponse response = new CommunityCommentReactionResponse(commentId, "LIKE", true, 2);
        when(mutationService.reactToComment(eq(viewerId), eq(commentId), eq(request))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.reactToComment(authentication(viewerId), commentId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).reactToComment(viewerId, commentId, request);
    }

    @Test
    void removeCommentReactionUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        CommunityCommentReactionResponse response = new CommunityCommentReactionResponse(commentId, "LIKE", false, 1);
        when(mutationService.removeCommentReaction(eq(viewerId), eq(commentId), eq("like"))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.removeCommentReaction(authentication(viewerId), commentId, "like");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).removeCommentReaction(viewerId, commentId, "like");
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
    void followProfileUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        CommunityFollowResponse response = new CommunityFollowResponse(targetProfileId, true);
        when(mutationService.followProfile(eq(viewerId), eq(targetProfileId))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.followProfile(authentication(viewerId), targetProfileId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).followProfile(viewerId, targetProfileId);
    }

    @Test
    void unfollowProfileUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        CommunityFollowResponse response = new CommunityFollowResponse(targetProfileId, false);
        when(mutationService.unfollowProfile(eq(viewerId), eq(targetProfileId))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.unfollowProfile(authentication(viewerId), targetProfileId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).unfollowProfile(viewerId, targetProfileId);
    }

    @Test
    void requestConnectionUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        CommunityConnectionResponse response = new CommunityConnectionResponse(
                relationshipId,
                targetProfileId,
                "pending_sent"
        );
        when(mutationService.requestConnection(eq(viewerId), eq(targetProfileId))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.requestConnection(authentication(viewerId), targetProfileId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).requestConnection(viewerId, targetProfileId);
    }

    @Test
    void updateConnectionStatusUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        CommunityConnectionStatusRequest request = new CommunityConnectionStatusRequest("accepted");
        CommunityConnectionResponse response = new CommunityConnectionResponse(
                relationshipId,
                targetProfileId,
                "connected"
        );
        when(mutationService.updateConnectionStatus(eq(viewerId), eq(relationshipId), eq(request)))
                .thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.updateConnectionStatus(
                authentication(viewerId),
                relationshipId,
                request
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).updateConnectionStatus(viewerId, relationshipId, request);
    }

    @Test
    void cancelConnectionRequestUsesAuthenticatedPrincipal() {
        UUID viewerId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        CommunityConnectionResponse response = new CommunityConnectionResponse(
                relationshipId,
                targetProfileId,
                "cancelled"
        );
        when(mutationService.cancelConnectionRequest(eq(viewerId), eq(relationshipId))).thenReturn(response);

        ResponseEntity<ApiResponse<?>> result = controller.cancelConnectionRequest(
                authentication(viewerId),
                relationshipId
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(response);
        verify(mutationService).cancelConnectionRequest(viewerId, relationshipId);
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

    private CommunityPostItem postItem(UUID postId, UUID viewerId) {
        return new CommunityPostItem(
                postId,
                viewerId,
                "Community post",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                OffsetDateTime.now(),
                new CommunityProfileSummary(
                        viewerId,
                        "Member",
                        "One",
                        null,
                        "MENTEE",
                        "",
                        null,
                        null,
                        false
                ),
                false,
                false,
                false,
                null,
                null,
                0
        );
    }
}
