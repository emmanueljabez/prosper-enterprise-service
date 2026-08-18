package com.prosper.prospermentor.service.community;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityReadServiceTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final CommunityReadService service = new CommunityReadService(jdbc);

    @Test
    void normalizesFeedModeToLatestWhenInputIsUnknown() {
        assertThat(service.normalizeFeedMode("popular")).isEqualTo("latest");
        assertThat(service.normalizeFeedMode("ranked")).isEqualTo("ranked");
        assertThat(service.normalizeFeedMode("latest")).isEqualTo("latest");
        assertThat(service.normalizeFeedMode(null)).isEqualTo("latest");
    }

    @Test
    void clampsLimitToSafeRange() {
        assertThat(service.clampLimit(-1)).isEqualTo(20);
        assertThat(service.clampLimit(0)).isEqualTo(20);
        assertThat(service.clampLimit(10)).isEqualTo(10);
        assertThat(service.clampLimit(200)).isEqualTo(50);
    }

    @Test
    void recommendationScoreUsesSharedSignals() {
        var viewer = Map.of(
                "role", "mentee",
                "industry", "Technology",
                "country", "Kenya",
                "interests", List.of("leadership", "product")
        );
        var candidate = Map.of(
                "role", "mentor",
                "industry", "technology",
                "country", "Kenya",
                "interests", List.of("product", "strategy")
        );

        assertThat(service.calculateRecommendationScore(viewer, candidate)).isGreaterThanOrEqualTo(70);
    }

    @Test
    void feedQueryUsesLegacyLinkPreviewColumns() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getFeed(UUID.randomUUID(), "latest", 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .doesNotContain("p.link_url")
                .doesNotContain("p.link_title")
                .doesNotContain("p.link_description")
                .doesNotContain("p.link_image")
                .contains("p.link_preview_url AS link_url")
                .contains("p.link_preview_title AS link_title")
                .contains("p.link_preview_description AS link_description")
                .contains("p.link_preview_image AS link_image");
    }

    @Test
    void feedQueryExcludesCommunityBlocksInBothDirections() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getFeed(UUID.randomUUID(), "latest", 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId")
                .contains("blocked_profile_id = p.user_id")
                .contains("blocker_profile_id = p.user_id")
                .contains("blocked_profile_id = :viewerId");
    }

    @Test
    void feedQueryIncludesViewerReactionAndSavedState() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getFeed(viewerId, "latest", 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM posts p")
                .contains("EXISTS (")
                .contains("FROM post_likes pl")
                .contains("pl.user_id = :viewerId")
                .contains("FROM saved_posts sp")
                .contains("sp.user_id = :viewerId")
                .contains("p.is_hidden AS is_hidden")
                .contains("p.link_preview_domain")
                .contains("p.link_preview_site_name");
    }

    @Test
    void singlePostQueryUsesLegacyPostsAndBlockFiltering() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getPost(viewerId, postId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Community post not found");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM posts p")
                .contains("p.id = :postId")
                .contains("community_blocks");
    }

    @Test
    void savedPostsQueryUsesLegacySavedPosts() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getSavedPosts(viewerId, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM saved_posts saved")
                .contains("JOIN posts p ON p.id = saved.post_id")
                .contains("saved.user_id = :viewerId");
    }

    @Test
    void recommendationQueryExcludesCommunityBlocksInBothDirections() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", viewerId,
                        "role", "mentee",
                        "industry", "Technology",
                        "country", "Kenya",
                        "interests", List.of("leadership")
                ));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        service.getRecommendedPeople(viewerId, 12);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sqlCaptor.capture(), any(MapSqlParameterSource.class));

        assertThat(sqlCaptor.getValue())
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId")
                .contains("blocked_profile_id = p.id")
                .contains("blocker_profile_id = p.id")
                .contains("blocked_profile_id = :viewerId");
    }

    @Test
    void normalizesSearchTypeToSupportedScopes() {
        assertThat(service.normalizeSearchType("posts")).isEqualTo("posts");
        assertThat(service.normalizeSearchType("people")).isEqualTo("people");
        assertThat(service.normalizeSearchType("categories")).isEqualTo("categories");
        assertThat(service.normalizeSearchType("hashtags")).isEqualTo("hashtags");
        assertThat(service.normalizeSearchType("unknown")).isEqualTo("all");
        assertThat(service.normalizeSearchType(null)).isEqualTo("all");
    }

    @Test
    void searchRejectsBlankQuery() {
        assertThatThrownBy(() -> service.search(UUID.randomUUID(), "   ", "all", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Search query is required");
    }

    @Test
    void searchAllQueriesPostsPeopleCategoriesAndHashtagsWithBlockFiltering() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.search(UUID.randomUUID(), "mentor", "all", 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getAllValues())
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM posts p")
                        .contains("community_blocks")
                        .contains("p.content ILIKE :searchPattern"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM profiles p")
                        .contains("community_blocks")
                        .contains("p.first_name ILIKE :searchPattern"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM community_categories c")
                        .contains("c.is_active = true"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("regexp_matches(p.content")
                        .contains("community_blocks"));
    }

    @Test
    void peopleDiscoveryQueriesRecentConnectionsWithBlockFiltering() {
        UUID viewerId = UUID.randomUUID();
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", viewerId,
                        "role", "mentee",
                        "industry", "Technology",
                        "country", "Kenya",
                        "interests", List.of("leadership")
                ));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.getPeopleDiscovery(viewerId, 8);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM syncs s")
                .contains("s.status = 'accepted'")
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId")
                .contains("blocked_profile_id = other_profile.id");
    }

    @Test
    void profileNetworkUsesPathProfileAsSubjectAndViewerForBlockFiltering() {
        UUID viewerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        var response = service.getProfileNetwork(viewerId, profileId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, atLeastOnce()).query(
                sqlCaptor.capture(),
                paramsCaptor.capture(),
                any(RowMapper.class)
        );

        assertThat(response.profileId()).isEqualTo(profileId);
        assertThat(response.totalCount()).isZero();
        assertThat(paramsCaptor.getAllValues())
                .allSatisfy(params -> {
                    assertThat(params.getValue("viewerId")).isEqualTo(viewerId);
                    assertThat(params.getValue("subjectId")).isEqualTo(profileId);
                });
        assertThat(sqlCaptor.getAllValues())
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM syncs s")
                        .contains("s.mentor_id = :subjectId")
                        .contains("s.mentee_id = :subjectId")
                        .contains("blocker_profile_id = :viewerId"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM follows f")
                        .contains("f.following_id = :subjectId")
                        .contains("blocker_profile_id = :viewerId"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM follows f")
                        .contains("f.follower_id = :subjectId")
                        .contains("blocker_profile_id = :viewerId"));
    }
}
