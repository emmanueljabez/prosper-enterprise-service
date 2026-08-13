package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityBlockRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityMutationServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityEventOutboxService outboxService = mock(CommunityEventOutboxService.class);
    private final CommunityMutationService service = new CommunityMutationService(jdbc, outboxService);

    @Test
    void normalizeReactionTypeDefaultsToLike() {
        assertThat(service.normalizeReactionType(null)).isEqualTo("LIKE");
        assertThat(service.normalizeReactionType("")).isEqualTo("LIKE");
        assertThat(service.normalizeReactionType("like")).isEqualTo("LIKE");
    }

    @Test
    void createPostRejectsBlankContent() {
        UUID viewerId = UUID.randomUUID();
        CommunityPostRequest request = new CommunityPostRequest(
                null,
                "  ",
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

        assertThatThrownBy(() -> service.createPost(viewerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Post content is required");
    }

    @Test
    void createPostStoresPostAndRecordsOutboxEvent() {
        UUID viewerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.update(contains("INSERT INTO community_posts"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        var result = service.createPost(viewerId, new CommunityPostRequest(
                categoryId,
                "What helped you prepare for a leadership interview?",
                "PUBLIC",
                null,
                null,
                null,
                "https://example.com/advice",
                "Interview advice",
                "Useful leadership interview context",
                "https://example.com/image.png",
                List.of("leadership", "interview")
        ));

        assertThat(result.id()).isNotNull();
        assertThat(result.userId()).isEqualTo(viewerId);
        assertThat(result.categoryId()).isEqualTo(categoryId);
        assertThat(result.content()).isEqualTo("What helped you prepare for a leadership interview?");
        assertThat(result.visibility()).isEqualTo("PUBLIC");
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_CREATED"),
                eq("POST"),
                eq(result.id()),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void reactToPostIsIdempotentAndOnlyRecordsOutboxWhenInserted() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.update(contains("INSERT INTO community_post_reactions"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbc.update(contains("UPDATE community_posts"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("COUNT(*)"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(4);

        var inserted = service.reactToPost(viewerId, postId, new CommunityReactionRequest("like"));

        assertThat(inserted.reacted()).isTrue();
        assertThat(inserted.likesCount()).isEqualTo(4);
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_REACTED"),
                eq("POST"),
                eq(postId),
                eq(viewerId),
                isNull(),
                anyMap()
        );

        reset(outboxService);
        when(jdbc.update(contains("INSERT INTO community_post_reactions"), any(MapSqlParameterSource.class)))
                .thenReturn(0);

        var duplicate = service.reactToPost(viewerId, postId, new CommunityReactionRequest("LIKE"));

        assertThat(duplicate.reacted()).isTrue();
        assertThat(duplicate.likesCount()).isEqualTo(4);
        verify(outboxService, never()).recordEvent(anyString(), anyString(), any(UUID.class), any(UUID.class), any(), anyMap());
    }

    @Test
    void savePostIsIdempotentAndReturnsLatestCounter() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.update(contains("INSERT INTO community_saved_posts"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbc.update(contains("UPDATE community_posts"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(contains("SELECT saves_count"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(3);

        var result = service.savePost(viewerId, postId);

        assertThat(result.postId()).isEqualTo(postId);
        assertThat(result.saved()).isTrue();
        assertThat(result.savesCount()).isEqualTo(3);
        verify(outboxService).recordEvent(
                eq("COMMUNITY_POST_SAVED"),
                eq("POST"),
                eq(postId),
                eq(viewerId),
                isNull(),
                anyMap()
        );
    }

    @Test
    void blockUserRejectsSelfBlock() {
        UUID viewerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.blockUser(viewerId, new CommunityBlockRequest(viewerId, "spam")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You cannot block yourself");
    }

    @Test
    void getNotificationPreferencesReturnsDefaultsWhenNoRowExists() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var result = service.getNotificationPreferences(viewerId);

        assertThat(result.profileId()).isEqualTo(viewerId);
        assertThat(result.inAppEnabled()).isTrue();
        assertThat(result.emailEnabled()).isTrue();
        assertThat(result.whatsappEnabled()).isFalse();
        assertThat(result.digestFrequency()).isEqualTo("DAILY");
    }

    @Test
    void updateNotificationPreferencesUpsertsAndRecordsOutbox() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.update(contains("INSERT INTO community_notification_preferences"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        var result = service.updateNotificationPreferences(viewerId, new CommunityNotificationPreferencesRequest(
                true,
                false,
                true,
                true,
                true,
                false,
                true,
                false,
                "WEEKLY",
                "21:00",
                "07:00"
        ));

        assertThat(result.profileId()).isEqualTo(viewerId);
        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.whatsappEnabled()).isTrue();
        assertThat(result.digestFrequency()).isEqualTo("WEEKLY");
        verify(outboxService).recordEvent(
                eq("COMMUNITY_NOTIFICATION_PREFERENCES_UPDATED"),
                eq("PROFILE"),
                eq(viewerId),
                eq(viewerId),
                eq(viewerId),
                anyMap()
        );
    }
}
