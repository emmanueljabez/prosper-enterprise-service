package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentReactionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionStatusRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostMutationResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityMutationServiceLegacyBridgeTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityEventOutboxService outboxService = mock(CommunityEventOutboxService.class);
    private final CommunityMutationService service = new CommunityMutationService(jdbc, outboxService);

    @Test
    void createPostWritesLegacyPostsSoB2cFeedCanReadIt() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.update(contains("INSERT INTO posts"), any(MapSqlParameterSource.class))).thenReturn(1);

        CommunityPostMutationResponse response = service.createPost(viewerId, new CommunityPostRequest(
                null,
                "Backend owned B2C post",
                "PUBLIC",
                "https://cdn.example.com/post.png",
                "image",
                null,
                "https://example.com",
                "Example",
                "Preview text",
                "https://example.com/preview.png",
                List.of("mentorship")
        ));

        assertThat(response.userId()).isEqualTo(viewerId);
        assertThat(response.content()).isEqualTo("Backend owned B2C post");
        verify(jdbc).update(contains("INSERT INTO posts"), any(MapSqlParameterSource.class));
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_CREATED"),
                eq("POST"),
                eq(response.id()),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void updateLegacyPostRequiresOwnerAndUpdatesPostsTable() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        legacyPostExistsForOwner();
        when(jdbc.update(contains("UPDATE posts"), any(MapSqlParameterSource.class))).thenReturn(1);

        CommunityPostMutationResponse response = service.updatePost(viewerId, postId, new CommunityPostRequest(
                null,
                "Updated B2C post",
                "PUBLIC",
                null,
                null,
                null,
                "https://example.com/updated",
                "Updated",
                "Updated preview",
                null,
                List.of()
        ));

        assertThat(response.id()).isEqualTo(postId);
        assertThat(response.content()).isEqualTo("Updated B2C post");
        verify(jdbc).update(contains("UPDATE posts"), any(MapSqlParameterSource.class));
    }

    @Test
    void deleteLegacyPostRequiresOwnerAndDeletesPostsTable() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        legacyPostExistsForOwner();
        when(jdbc.update(contains("DELETE FROM posts"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.deletePost(viewerId, postId);

        assertThat(response).containsEntry("postId", postId).containsEntry("deleted", true);
        verify(jdbc).update(contains("DELETE FROM posts"), any(MapSqlParameterSource.class));
    }

    @Test
    void setLegacyPostHiddenUpdatesIsHidden() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        legacyPostExistsForOwner();
        when(jdbc.update(contains("UPDATE posts"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.setPostHidden(viewerId, postId, new CommunityPostHiddenRequest(true));

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.hidden()).isTrue();
        verify(jdbc).update(contains("SET is_hidden = :hidden"), any(MapSqlParameterSource.class));
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_HIDDEN"),
                eq("POST"),
                eq(postId),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void reactToLegacyPostUsesPostLikesAndReturnsCanonicalCount() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        visibleLegacyPost();
        when(jdbc.update(contains("INSERT INTO post_likes"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.update(contains("UPDATE posts"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM post_likes"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(7);

        var response = service.reactToPost(viewerId, postId, new CommunityReactionRequest("like"));

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.reacted()).isTrue();
        assertThat(response.likesCount()).isEqualTo(7);
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_REACTED"),
                eq("POST"),
                eq(postId),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void saveLegacyPostUsesSavedPostsAndReturnsTrue() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        visibleLegacyPost();
        when(jdbc.update(contains("INSERT INTO saved_posts"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM saved_posts"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(3);

        var response = service.savePost(viewerId, postId);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.saved()).isTrue();
        assertThat(response.savesCount()).isEqualTo(3);
        verify(jdbc).update(contains("INSERT INTO saved_posts"), any(MapSqlParameterSource.class));
    }

    @Test
    void unsaveLegacyPostUsesSavedPostsAndReturnsFalse() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(jdbc.update(contains("DELETE FROM saved_posts"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM saved_posts"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(2);

        var response = service.unsavePost(viewerId, postId);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.saved()).isFalse();
        assertThat(response.savesCount()).isEqualTo(2);
        verify(jdbc).update(contains("DELETE FROM saved_posts"), any(MapSqlParameterSource.class));
    }

    @Test
    void createLegacyCommentInsertsPostCommentsAndReturnsAuthorlessCreatedComment() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        visibleLegacyPost();
        when(jdbc.update(contains("INSERT INTO post_comments"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.update(contains("UPDATE posts"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.createComment(viewerId, postId, new CommunityCommentRequest(null, "Useful advice"));

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.authorProfileId()).isEqualTo(viewerId);
        assertThat(response.content()).isEqualTo("Useful advice");
        verify(outboxService).recordEvent(
                eq("COMMUNITY_COMMENT_CREATED"),
                eq("COMMENT"),
                eq(response.id()),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void reportLegacyPostAcceptsPostsTargetAndRecordsOutbox() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(jdbc.queryForObject(contains("FROM community_posts"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(false);
        when(jdbc.queryForObject(contains("FROM posts"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.update(contains("INSERT INTO community_reports"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.reportContent(viewerId, new CommunityReportRequest(
                "POST",
                postId,
                "spam",
                "Looks suspicious"
        ));

        assertThat(response.targetId()).isEqualTo(postId);
        assertThat(response.status()).isEqualTo("OPEN");
        verify(outboxService).recordEvent(
                eq("COMMUNITY_CONTENT_REPORTED"),
                eq("POST"),
                eq(postId),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void reactToLegacyCommentUsesCommentLikesAndReturnsCount() {
        UUID viewerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        visibleLegacyComment();
        when(jdbc.update(contains("INSERT INTO comment_likes"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM comment_likes"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(5);

        CommunityCommentReactionResponse response = service.reactToComment(
                viewerId,
                commentId,
                new CommunityReactionRequest("like")
        );

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.reacted()).isTrue();
        assertThat(response.likesCount()).isEqualTo(5);
        verify(outboxService).recordEvent(
                eq("COMMUNITY_COMMENT_REACTED"),
                eq("COMMENT"),
                eq(commentId),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void removeLegacyCommentReactionDeletesCommentLikesAndReturnsCount() {
        UUID viewerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        visibleLegacyComment();
        when(jdbc.update(contains("DELETE FROM comment_likes"), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM comment_likes"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(4);

        CommunityCommentReactionResponse response = service.removeCommentReaction(viewerId, commentId, "like");

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.reacted()).isFalse();
        assertThat(response.likesCount()).isEqualTo(4);
        verify(jdbc).update(contains("DELETE FROM comment_likes"), any(MapSqlParameterSource.class));
    }

    @Test
    void followProfileWritesFollowsAndRecordsConnectionEvent() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        profileExists(targetProfileId);
        when(jdbc.update(contains("INSERT INTO follows"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.followProfile(viewerId, targetProfileId);

        assertThat(response.targetProfileId()).isEqualTo(targetProfileId);
        assertThat(response.following()).isTrue();
        verify(jdbc).update(contains("INSERT INTO follows"), any(MapSqlParameterSource.class));
        verify(outboxService).recordEvent(
                eq("COMMUNITY_PROFILE_FOLLOWED"),
                eq("PROFILE"),
                eq(targetProfileId),
                eq(viewerId),
                eq(targetProfileId),
                anyMap()
        );
    }

    @Test
    void unfollowProfileDeletesFollowsAndRecordsConnectionEvent() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        when(jdbc.update(contains("DELETE FROM follows"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.unfollowProfile(viewerId, targetProfileId);

        assertThat(response.targetProfileId()).isEqualTo(targetProfileId);
        assertThat(response.following()).isFalse();
        verify(jdbc).update(contains("DELETE FROM follows"), any(MapSqlParameterSource.class));
        verify(outboxService).recordEvent(
                eq("COMMUNITY_PROFILE_UNFOLLOWED"),
                eq("PROFILE"),
                eq(targetProfileId),
                eq(viewerId),
                eq(targetProfileId),
                anyMap()
        );
    }

    @Test
    void requestConnectionWritesSyncsWithCanonicalPairAndRequester() {
        UUID viewerId = UUID.fromString("10000000-0000-0000-0000-000000000000");
        UUID targetProfileId = UUID.fromString("20000000-0000-0000-0000-000000000000");
        profileExists(targetProfileId);
        when(jdbc.query(contains("FROM syncs"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO syncs"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.requestConnection(viewerId, targetProfileId);

        assertThat(response.targetProfileId()).isEqualTo(targetProfileId);
        assertThat(response.relationshipStatus()).isEqualTo("pending_sent");
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO syncs"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("mentorId")).isEqualTo(viewerId);
        assertThat(paramsCaptor.getValue().getValue("menteeId")).isEqualTo(targetProfileId);
        assertThat(paramsCaptor.getValue().getValue("requesterId")).isEqualTo(viewerId);
        verify(outboxService).recordEvent(
                eq("COMMUNITY_CONNECTION_REQUESTED"),
                eq("SYNC"),
                eq(response.relationshipId()),
                eq(viewerId),
                eq(targetProfileId),
                anyMap()
        );
    }

    @Test
    void updateConnectionStatusAcceptsPendingRequestForRecipient() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(jdbc.queryForMap(contains("FROM syncs"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", relationshipId,
                        "mentor_id", viewerId,
                        "mentee_id", targetProfileId,
                        "requester_id", targetProfileId,
                        "status", "pending"
                ));
        when(jdbc.update(contains("UPDATE syncs"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.updateConnectionStatus(
                viewerId,
                relationshipId,
                new CommunityConnectionStatusRequest("accepted")
        );

        assertThat(response.relationshipId()).isEqualTo(relationshipId);
        assertThat(response.targetProfileId()).isEqualTo(targetProfileId);
        assertThat(response.relationshipStatus()).isEqualTo("connected");
        verify(outboxService).recordEvent(
                eq("COMMUNITY_CONNECTION_ACCEPTED"),
                eq("SYNC"),
                eq(relationshipId),
                eq(viewerId),
                eq(targetProfileId),
                anyMap()
        );
    }

    @Test
    void cancelConnectionRequestDeletesRequesterPendingSync() {
        UUID viewerId = UUID.randomUUID();
        UUID targetProfileId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(jdbc.queryForMap(contains("FROM syncs"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", relationshipId,
                        "mentor_id", viewerId,
                        "mentee_id", targetProfileId,
                        "requester_id", viewerId,
                        "status", "pending"
                ));
        when(jdbc.update(contains("DELETE FROM syncs"), any(MapSqlParameterSource.class))).thenReturn(1);

        var response = service.cancelConnectionRequest(viewerId, relationshipId);

        assertThat(response.relationshipId()).isEqualTo(relationshipId);
        assertThat(response.targetProfileId()).isEqualTo(targetProfileId);
        assertThat(response.relationshipStatus()).isEqualTo("cancelled");
        verify(jdbc).update(contains("DELETE FROM syncs"), any(MapSqlParameterSource.class));
        verify(outboxService).recordEvent(
                eq("COMMUNITY_CONNECTION_CANCELLED"),
                eq("SYNC"),
                eq(relationshipId),
                eq(viewerId),
                eq(targetProfileId),
                anyMap()
        );
    }

    private void visibleLegacyPost() {
        when(jdbc.queryForObject(contains("FROM community_posts"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(false);
        when(jdbc.queryForObject(contains("FROM posts p"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
    }

    private void legacyPostExistsForOwner() {
        when(jdbc.queryForObject(contains("FROM community_posts"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(false);
        when(jdbc.queryForObject(contains("FROM posts"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
    }

    private void visibleLegacyComment() {
        when(jdbc.queryForObject(contains("FROM post_comments c"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
    }

    private void profileExists(UUID profileId) {
        when(jdbc.queryForObject(contains("FROM profiles"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
    }
}
