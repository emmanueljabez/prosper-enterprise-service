package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionProfile;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionRequestItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostLikeItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostLikesResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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
    void postLikesQueryReturnsVisibleLegacyPostLikers() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID likerId = UUID.randomUUID();
        OffsetDateTime likedAt = OffsetDateTime.now();
        CommunityPostLikeItem liker = new CommunityPostLikeItem(
                likerId,
                likedAt,
                new CommunityProfileSummary(
                        likerId,
                        "Ada",
                        "Lovelace",
                        "https://cdn.example.com/ada.png",
                        "MENTEE",
                        "Builder",
                        "Technology",
                        "Kenya",
                        true
                )
        );
        when(jdbc.queryForObject(contains("SELECT EXISTS"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(liker));
        when(jdbc.queryForObject(contains("FROM post_likes pl"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        CommunityPostLikesResponse response = service.getPostLikes(viewerId, postId, 20);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.likesCount()).isEqualTo(1);
        assertThat(response.people()).containsExactly(liker);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM post_likes pl")
                .contains("JOIN profiles liker ON liker.id = pl.user_id")
                .doesNotContain("community_post_reactions")
                .contains("community_blocks")
                .contains("ORDER BY liked_at DESC");
    }

    @Test
    void postLikesQueryReturnsVisibleCommunityPostLikers() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID likerId = UUID.randomUUID();
        OffsetDateTime likedAt = OffsetDateTime.now();
        CommunityPostLikeItem liker = new CommunityPostLikeItem(
                likerId,
                likedAt,
                new CommunityProfileSummary(
                        likerId,
                        "Grace",
                        "Hopper",
                        "https://cdn.example.com/grace.png",
                        "MENTOR",
                        "Compiler pioneer",
                        "Technology",
                        "Kenya",
                        true
                )
        );
        when(jdbc.queryForObject(contains("FROM community_posts p"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(liker));
        when(jdbc.queryForObject(contains("FROM community_post_reactions cpr"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        CommunityPostLikesResponse response = service.getPostLikes(viewerId, postId, 20);

        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.likesCount()).isEqualTo(1);
        assertThat(response.people()).containsExactly(liker);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("FROM community_post_reactions cpr")
                .contains("JOIN profiles liker ON liker.id = cpr.user_profile_id")
                .doesNotContain("FROM post_likes pl")
                .contains("community_blocks")
                .contains("ORDER BY liked_at DESC");
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

    @Test
    void homeDashboardComposesFeedStatsRecommendationsAndLearningSummary() {
        UUID viewerId = UUID.randomUUID();
        UUID mentorId = UUID.randomUUID();
        when(jdbc.query(contains("FROM posts p"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForMap(contains("SELECT id, role::text AS role"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "id", viewerId,
                        "role", "mentee",
                        "industry", "Technology",
                        "country", "Kenya",
                        "interests", List.of("leadership")
                ));
        when(jdbc.queryForList(contains("FROM profiles p"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", mentorId,
                        "first_name", "Faith",
                        "last_name", "Wainana",
                        "avatar_url", "",
                        "role", "mentor",
                        "headline", "Leadership coach",
                        "industry", "Technology",
                        "country", "Kenya",
                        "is_verified", true,
                        "interests", List.of("leadership")
                )));
        when(jdbc.queryForMap(contains("connections_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "connections_count", 2L,
                        "followers_count", 3L,
                        "following_count", 4L,
                        "sessions_count", 5L,
                        "posts_count", 6L
                ));
        when(jdbc.query(contains("FROM sessions s"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("to_regclass"), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(true);
        when(jdbc.queryForObject(contains("FROM messages m"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(7);
        when(jdbc.queryForObject(contains("FROM profile_views pv"), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(8);
        when(jdbc.queryForMap(contains("profile_completion_percent"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of("profile_completion_percent", 65));

        var response = service.getHome(viewerId, 12, 3);

        assertThat(response.feed().mode()).isEqualTo("ranked");
        assertThat(response.feed().limit()).isEqualTo(12);
        assertThat(response.stats().connections()).isEqualTo(2);
        assertThat(response.stats().followers()).isEqualTo(3);
        assertThat(response.stats().following()).isEqualTo(4);
        assertThat(response.stats().sessions()).isEqualTo(5);
        assertThat(response.stats().articles()).isEqualTo(6);
        assertThat(response.stats().messages()).isEqualTo(7);
        assertThat(response.stats().profileViews()).isEqualTo(8);
        assertThat(response.stats().recommendations()).isEqualTo(1);
        assertThat(response.recommendedMentors()).hasSize(1);
        assertThat(response.recommendedMentors().get(0).profile().role()).isEqualTo("mentor");
        assertThat(response.profileCompletionPercent()).isEqualTo(65);
        assertThat(response.learning().progressPercent()).isEqualTo(33);
        assertThat(response.learning().assignments())
                .extracting("title")
                .contains("Complete your profile", "Book your next mentorship session", "Share a community post");
    }

    @Test
    void connectionRequestsCategorizeRowsAndFilterBlocksForViewer() {
        UUID viewerId = UUID.randomUUID();
        UUID incomingRequester = UUID.randomUUID();
        UUID outgoingTarget = UUID.randomUUID();
        UUID acceptedTarget = UUID.randomUUID();
        UUID rejectedTarget = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        connectionRequest(viewerId, incomingRequester, incomingRequester, "pending"),
                        connectionRequest(viewerId, outgoingTarget, viewerId, "pending"),
                        connectionRequest(viewerId, acceptedTarget, acceptedTarget, "accepted"),
                        connectionRequest(viewerId, rejectedTarget, viewerId, "rejected")
                ));

        var response = service.getConnectionRequests(viewerId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        assertThat(response.incoming()).hasSize(1);
        assertThat(response.outgoing()).hasSize(1);
        assertThat(response.accepted()).hasSize(1);
        assertThat(response.rejected()).hasSize(1);
        assertThat(response.incoming().get(0).requesterId()).isEqualTo(incomingRequester);
        assertThat(response.outgoing().get(0).requesterId()).isEqualTo(viewerId);
        assertThat(paramsCaptor.getValue().getValue("viewerId")).isEqualTo(viewerId);
        assertThat(sqlCaptor.getValue())
                .contains("FROM syncs s")
                .contains("s.status IN ('pending', 'accepted', 'rejected')")
                .contains("s.mentor_id = :viewerId")
                .contains("s.mentee_id = :viewerId")
                .contains("community_blocks")
                .contains("blocker_profile_id = :viewerId");
    }

    @Test
    void profileAnalyticsRequiresProfileOwner() {
        UUID viewerId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getProfileAnalytics(viewerId, profileId, 50))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Profile analytics are only available to the profile owner");
    }

    @Test
    void profileAnalyticsQueriesProfileViewsAndPostImpressionsForOwner() {
        UUID profileId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "total_views", 4L,
                        "views_this_week", 2L,
                        "views_this_month", 3L,
                        "views_previous_week", 1L,
                        "views_previous_month", 6L
                ));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(9);

        var response = service.getProfileAnalytics(profileId, profileId, 50);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mapQueryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> objectQueryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, atLeastOnce()).query(queryCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        verify(jdbc).queryForMap(mapQueryCaptor.capture(), any(MapSqlParameterSource.class));
        verify(jdbc).queryForObject(objectQueryCaptor.capture(), any(MapSqlParameterSource.class), eq(Integer.class));

        assertThat(response.profileId()).isEqualTo(profileId);
        assertThat(response.totalViews()).isEqualTo(4);
        assertThat(response.viewsThisWeek()).isEqualTo(2);
        assertThat(response.viewsThisMonth()).isEqualTo(3);
        assertThat(response.postImpressionsThisMonth()).isEqualTo(9);
        assertThat(response.connectionRequests()).isZero();
        assertThat(response.weeklyGrowth()).isEqualTo(100.0);
        assertThat(response.monthlyGrowth()).isEqualTo(-50.0);
        assertThat(queryCaptor.getAllValues())
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM profile_views pv")
                        .contains("LEFT JOIN profiles viewer ON viewer.id = pv.viewer_id")
                        .contains("pv.viewed_profile_id = :profileId")
                        .contains("pv.viewer_id IS DISTINCT FROM :profileId")
                        .contains("community_blocks"))
                .anySatisfy(sql -> assertThat(sql)
                        .contains("FROM syncs s")
                        .contains("s.status IN ('pending', 'accepted', 'rejected')"));
        assertThat(mapQueryCaptor.getValue())
                .contains("FROM profile_views pv")
                .contains("COUNT(*) FILTER")
                .contains("pv.viewed_profile_id = :profileId")
                .contains("pv.viewer_id IS DISTINCT FROM :profileId");
        assertThat(objectQueryCaptor.getValue())
                .contains("FROM post_impressions pi")
                .contains("JOIN posts p ON p.id = pi.post_id")
                .contains("p.user_id = :profileId")
                .contains("COALESCE(pi.created_at, pi.viewed_at) >= :startThisMonth");
        assertThat(paramsCaptor.getAllValues())
                .anySatisfy(params -> {
                    assertThat(params.getValue("viewerId")).isEqualTo(profileId);
                    assertThat(params.getValue("profileId")).isEqualTo(profileId);
                });
    }

    private CommunityConnectionRequestItem connectionRequest(
            UUID viewerId,
            UUID otherProfileId,
            UUID requesterId,
            String status
    ) {
        UUID mentorId = viewerId;
        UUID menteeId = otherProfileId;
        return new CommunityConnectionRequestItem(
                UUID.randomUUID(),
                mentorId,
                menteeId,
                requesterId,
                status,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                connectionProfile(mentorId),
                connectionProfile(menteeId)
        );
    }

    private CommunityConnectionProfile connectionProfile(UUID profileId) {
        return new CommunityConnectionProfile(
                profileId,
                "First",
                "Last",
                null,
                "member@example.com",
                "MENTEE",
                "Profile headline",
                "Technology",
                "Kenya",
                true
        );
    }
}
