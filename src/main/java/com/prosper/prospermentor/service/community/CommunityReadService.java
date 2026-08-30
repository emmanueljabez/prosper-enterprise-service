package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFeedResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHomeAssignment;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHomeLearningSummary;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHomeResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHomeSession;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHomeStats;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCategoryItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionProfile;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionRequestItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionRequestsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityHashtagItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityMyPostsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPeopleDiscoveryResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileAnalyticsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileNetworkResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileSummary;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileViewItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySavedPostsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySearchPersonItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySearchResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.NetworkMember;
import com.prosper.prospermentor.dto.community.CommunityDtos.NetworkOverviewResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendationReason;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendedPeopleResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendedPerson;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityReadService {
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public CommunityHomeResponse getHome(UUID viewerId, int requestedFeedLimit, int requestedPeopleLimit) {
        requireViewer(viewerId);
        int feedLimit = clampLimit(requestedFeedLimit);
        int peopleLimit = clampHomePeopleLimit(requestedPeopleLimit);

        CommunityFeedResponse feed = getFeed(viewerId, "ranked", feedLimit);
        List<RecommendedPerson> recommendedMentors = queryRecommendedPeople(viewerId, peopleLimit, true);
        Map<String, Object> statsRow = queryHomeStats(viewerId);
        CommunityHomeSession nextSession = queryNextSession(viewerId).orElse(null);
        int profileCompletionPercent = queryProfileCompletionPercent(viewerId);
        CommunityHomeStats stats = new CommunityHomeStats(
                intValue(statsRow, "connections_count"),
                intValue(statsRow, "followers_count"),
                intValue(statsRow, "following_count"),
                intValue(statsRow, "sessions_count"),
                intValue(statsRow, "posts_count"),
                intValue(statsRow, "unread_messages_count"),
                recommendedMentors.size(),
                intValue(statsRow, "profile_views_count")
        );

        return new CommunityHomeResponse(
                feed,
                stats,
                nextSession,
                recommendedMentors,
                profileCompletionPercent,
                buildLearningSummary(stats, nextSession, profileCompletionPercent)
        );
    }

    @Transactional(readOnly = true)
    public CommunityFeedResponse getFeed(UUID viewerId, String mode, int requestedLimit) {
        requireViewer(viewerId);
        String normalizedMode = normalizeFeedMode(mode);
        int limit = clampLimit(requestedLimit);
        String orderBy = "ranked".equals(normalizedMode)
                ? "(COALESCE(p.likes_count, 0) * 3 + COALESCE(p.comments_count, 0) * 5) DESC, p.created_at DESC"
                : "p.created_at DESC";

        String sql = """
                %s
                FROM posts p
                JOIN profiles author ON author.id = p.user_id
                WHERE (COALESCE(p.is_hidden, false) = false
                   OR p.user_id = :viewerId)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                       OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY %s
                LIMIT :limit
                """.formatted(legacyPostSelectColumns(), orderBy);

        var parameters = new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("limit", limit);

        List<CommunityPostItem> posts = jdbc.query(sql, parameters, postRowMapper());
        return new CommunityFeedResponse(posts, normalizedMode, limit);
    }

    @Transactional(readOnly = true)
    public CommunityPostItem getPost(UUID viewerId, UUID postId) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");

        String sql = """
                %s
                FROM posts p
                JOIN profiles author ON author.id = p.user_id
                WHERE p.id = :postId
                  AND (COALESCE(p.is_hidden, false) = false
                   OR p.user_id = :viewerId)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                       OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                  )
                LIMIT 1
                """.formatted(legacyPostSelectColumns());

        List<CommunityPostItem> posts = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), postRowMapper());
        if (posts.isEmpty()) {
            throw new NoSuchElementException("Community post not found");
        }
        return posts.get(0);
    }

    @Transactional(readOnly = true)
    public CommunitySavedPostsResponse getSavedPosts(UUID viewerId, int requestedLimit) {
        requireViewer(viewerId);
        int limit = clampLimit(requestedLimit);

        String sql = """
                %s
                FROM saved_posts saved
                JOIN posts p ON p.id = saved.post_id
                JOIN profiles author ON author.id = p.user_id
                WHERE saved.user_id = :viewerId
                  AND (COALESCE(p.is_hidden, false) = false
                   OR p.user_id = :viewerId)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                       OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY saved.created_at DESC
                LIMIT :limit
                """.formatted(legacyPostSelectColumns());

        List<CommunityPostItem> posts = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("limit", limit), postRowMapper());
        return new CommunitySavedPostsResponse(posts, limit);
    }

    @Transactional(readOnly = true)
    public CommunityMyPostsResponse getMyPosts(UUID viewerId, int requestedLimit) {
        requireViewer(viewerId);
        int limit = clampLimit(requestedLimit);

        String sql = """
                %s
                FROM posts p
                JOIN profiles author ON author.id = p.user_id
                WHERE p.user_id = :viewerId
                ORDER BY p.created_at DESC
                LIMIT :limit
                """.formatted(legacyPostSelectColumns());

        List<CommunityPostItem> posts = jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("limit", limit), postRowMapper());
        return new CommunityMyPostsResponse(posts, limit);
    }

    @Transactional(readOnly = true)
    public RecommendedPeopleResponse getRecommendedPeople(UUID viewerId, int requestedLimit) {
        requireViewer(viewerId);
        int limit = clampLimit(requestedLimit);

        return new RecommendedPeopleResponse(queryRecommendedPeople(viewerId, limit, false), limit);
    }

    private List<RecommendedPerson> queryRecommendedPeople(UUID viewerId, int limit, boolean mentorsOnly) {
        Map<String, Object> viewer = jdbc.queryForMap("""
                SELECT id, role::text AS role, industry, country, interests
                FROM profiles
                WHERE id = :viewerId
                """, new MapSqlParameterSource("viewerId", viewerId));

        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT
                    p.id,
                    p.first_name,
                    p.last_name,
                    p.avatar_url,
                    p.role::text AS role,
                    COALESCE(p.bio, '') AS headline,
                    p.industry,
                    p.country,
                    COALESCE(p.is_verified, false) AS is_verified,
                    p.interests
                FROM profiles p
                WHERE p.id <> :viewerId
                  AND (:mentorsOnly = false OR LOWER(p.role::text) = 'mentor')
                  AND NOT EXISTS (
                    SELECT 1 FROM syncs s
                    WHERE ((s.mentor_id = :viewerId AND s.mentee_id = p.id)
                        OR (s.mentor_id = p.id AND s.mentee_id = :viewerId))
                      AND s.status IN ('pending', 'accepted')
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM follows f
                    WHERE f.follower_id = :viewerId
                      AND f.following_id = p.id
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                       OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                  )
                LIMIT 100
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("mentorsOnly", mentorsOnly));

        return candidates.stream()
                .map(candidate -> toRecommendedPerson(viewer, candidate))
                .filter(person -> person.score() > 0)
                .sorted(Comparator.comparingInt(RecommendedPerson::score).reversed())
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public NetworkOverviewResponse getNetworkOverview(UUID viewerId) {
        requireViewer(viewerId);
        return new NetworkOverviewResponse(
                queryNetworkMembers(viewerId, "connections"),
                queryNetworkMembers(viewerId, "followers"),
                queryNetworkMembers(viewerId, "following"),
                queryNetworkMembers(viewerId, "pendingRequests"),
                queryNetworkMembers(viewerId, "sentRequests")
        );
    }

    @Transactional(readOnly = true)
    public CommunityProfileNetworkResponse getProfileNetwork(UUID viewerId, UUID profileId) {
        requireViewer(viewerId);
        requireId(profileId, "profileId is required");

        List<NetworkMember> connections = queryNetworkMembers(viewerId, profileId, "connections");
        List<NetworkMember> followers = queryNetworkMembers(viewerId, profileId, "followers");
        List<NetworkMember> following = queryNetworkMembers(viewerId, profileId, "following");
        List<NetworkMember> reciprocalFollows = reciprocalFollows(followers, following);

        return new CommunityProfileNetworkResponse(
                profileId,
                connections,
                followers,
                following,
                reciprocalFollows,
                uniqueNetworkCount(connections, followers, following)
        );
    }

    @Transactional(readOnly = true)
    public CommunityProfileAnalyticsResponse getProfileAnalytics(UUID viewerId, UUID profileId, int requestedLimit) {
        requireViewer(viewerId);
        requireId(profileId, "profileId is required");
        if (!viewerId.equals(profileId)) {
            throw new SecurityException("Profile analytics are only available to the profile owner");
        }

        int limit = clampLimit(requestedLimit);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime oneWeekAgo = now.minusDays(7);
        OffsetDateTime previousWeekStart = oneWeekAgo.minusDays(7);
        OffsetDateTime oneMonthAgo = now.minusDays(30);
        OffsetDateTime previousMonthStart = oneMonthAgo.minusDays(30);
        OffsetDateTime startThisMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        List<CommunityProfileViewItem> views = queryProfileViews(viewerId, profileId, limit);
        Map<String, Object> viewStats = queryProfileViewStats(
                viewerId,
                profileId,
                oneWeekAgo,
                previousWeekStart,
                startThisMonth,
                oneMonthAgo,
                previousMonthStart
        );
        int postImpressionsThisMonth = queryPostImpressionsThisMonth(profileId, startThisMonth);
        CommunityConnectionRequestsResponse connectionRequests = getConnectionRequests(profileId);
        int pendingRequests = connectionRequests.incoming().size() + connectionRequests.outgoing().size();
        int viewsThisWeek = intValue(viewStats, "views_this_week");
        int viewsThisMonth = intValue(viewStats, "views_this_month");

        return new CommunityProfileAnalyticsResponse(
                profileId,
                views,
                intValue(viewStats, "total_views"),
                viewsThisWeek,
                viewsThisMonth,
                postImpressionsThisMonth,
                pendingRequests,
                growthPercentage(viewsThisWeek, intValue(viewStats, "views_previous_week")),
                growthPercentage(viewsThisMonth, intValue(viewStats, "views_previous_month"))
        );
    }

    @Transactional(readOnly = true)
    public CommunityConnectionRequestsResponse getConnectionRequests(UUID viewerId) {
        requireViewer(viewerId);

        List<CommunityConnectionRequestItem> requests = queryConnectionRequests(viewerId);

        return new CommunityConnectionRequestsResponse(
                requests.stream()
                        .filter(request -> "pending".equalsIgnoreCase(request.status()))
                        .filter(request -> !viewerId.equals(request.requesterId()))
                        .toList(),
                requests.stream()
                        .filter(request -> "pending".equalsIgnoreCase(request.status()))
                        .filter(request -> viewerId.equals(request.requesterId()))
                        .toList(),
                requests.stream()
                        .filter(request -> "accepted".equalsIgnoreCase(request.status()))
                        .toList(),
                requests.stream()
                        .filter(request -> "rejected".equalsIgnoreCase(request.status()))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public CommunitySearchResponse search(UUID viewerId, String query, String type, int requestedLimit) {
        requireViewer(viewerId);
        String normalizedQuery = requireSearchQuery(query);
        String normalizedType = normalizeSearchType(type);
        int limit = clampLimit(requestedLimit);

        return new CommunitySearchResponse(
                normalizedQuery,
                normalizedType,
                limit,
                shouldSearch(normalizedType, "posts") ? searchPosts(viewerId, normalizedQuery, limit) : List.of(),
                shouldSearch(normalizedType, "people") ? searchPeople(viewerId, normalizedQuery, limit) : List.of(),
                shouldSearch(normalizedType, "categories") ? searchCategories(normalizedQuery, limit) : List.of(),
                shouldSearch(normalizedType, "hashtags") ? searchHashtags(viewerId, normalizedQuery, limit) : List.of()
        );
    }

    @Transactional(readOnly = true)
    public CommunityPeopleDiscoveryResponse getPeopleDiscovery(UUID viewerId, int requestedLimit) {
        requireViewer(viewerId);
        int limit = clampLimit(requestedLimit);
        RecommendedPeopleResponse suggested = getRecommendedPeople(viewerId, limit);

        return new CommunityPeopleDiscoveryResponse(
                suggested.people(),
                queryRecentConnections(viewerId, limit),
                limit
        );
    }

    private Map<String, Object> queryHomeStats(UUID viewerId) {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)::int
                        FROM syncs s
                        JOIN profiles other_profile ON other_profile.id = CASE
                            WHEN s.mentor_id = :viewerId THEN s.mentee_id
                            ELSE s.mentor_id
                        END
                        WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                          AND s.status = 'accepted'
                          AND NOT EXISTS (
                            SELECT 1
                            FROM community_blocks b
                            WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = other_profile.id)
                               OR (b.blocker_profile_id = other_profile.id AND b.blocked_profile_id = :viewerId)
                          )
                    ) AS connections_count,
                    (
                        SELECT COUNT(*)::int
                        FROM follows f
                        JOIN profiles p ON p.id = f.follower_id
                        WHERE f.following_id = :viewerId
                          AND NOT EXISTS (
                            SELECT 1
                            FROM community_blocks b
                            WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                               OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                          )
                    ) AS followers_count,
                    (
                        SELECT COUNT(*)::int
                        FROM follows f
                        JOIN profiles p ON p.id = f.following_id
                        WHERE f.follower_id = :viewerId
                          AND NOT EXISTS (
                            SELECT 1
                            FROM community_blocks b
                            WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                               OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                          )
                    ) AS following_count,
                    (
                        SELECT COUNT(*)::int
                        FROM sessions s
                        WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                          AND COALESCE(UPPER(s.status::text), '') NOT IN ('CANCELLED', 'NO_SHOW')
                    ) AS sessions_count,
                    (
                        SELECT COUNT(*)::int
                        FROM posts p
                        WHERE p.user_id = :viewerId
                          AND COALESCE(p.is_hidden, false) = false
                    ) AS posts_count
                """;

        Map<String, Object> stats = new HashMap<>(
                jdbc.queryForMap(sql, new MapSqlParameterSource("viewerId", viewerId))
        );
        stats.put("unread_messages_count", queryUnreadMessagesCount(viewerId));
        stats.put("profile_views_count", queryProfileViewsCount(viewerId));
        return stats;
    }

    private int queryUnreadMessagesCount(UUID viewerId) {
        return queryOptionalTableCount(
                "public.messages",
                """
                        SELECT COUNT(*)::int
                        FROM messages m
                        WHERE m.recipient_id = :viewerId
                          AND m.read_at IS NULL
                        """,
                new MapSqlParameterSource("viewerId", viewerId)
        );
    }

    private int queryProfileViewsCount(UUID viewerId) {
        return queryOptionalTableCount(
                "public.profile_views",
                """
                        SELECT COUNT(*)::int
                        FROM profile_views pv
                        WHERE pv.viewed_profile_id = :viewerId
                          AND pv.viewer_id IS DISTINCT FROM :viewerId
                          AND (
                            pv.viewer_id IS NULL
                            OR NOT EXISTS (
                                SELECT 1
                                FROM community_blocks b
                                WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = pv.viewer_id)
                                   OR (b.blocker_profile_id = pv.viewer_id AND b.blocked_profile_id = :viewerId)
                            )
                          )
                        """,
                new MapSqlParameterSource("viewerId", viewerId)
        );
    }

    private int queryOptionalTableCount(String tableName, String countSql, MapSqlParameterSource parameters) {
        if (!tableExists(tableName)) {
            return 0;
        }
        try {
            Integer count = jdbc.queryForObject(countSql, parameters, Integer.class);
            return count == null ? 0 : count;
        } catch (DataAccessException ignored) {
            return 0;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT to_regclass(:tableName) IS NOT NULL",
                    new MapSqlParameterSource("tableName", tableName),
                    Boolean.class
            ));
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private Optional<CommunityHomeSession> queryNextSession(UUID viewerId) {
        String sql = """
                SELECT
                    s.id,
                    s.title,
                    s.scheduled_start,
                    s.scheduled_end,
                    s.status::text AS status,
                    s.meeting_url,
                    participant.id AS participant_id,
                    participant.first_name AS participant_first_name,
                    participant.last_name AS participant_last_name,
                    participant.avatar_url AS participant_avatar_url,
                    participant.role::text AS participant_role,
                    COALESCE(participant.bio, '') AS participant_headline,
                    participant.industry AS participant_industry,
                    participant.country AS participant_country,
                    COALESCE(participant.is_verified, false) AS participant_is_verified
                FROM sessions s
                LEFT JOIN profiles participant ON participant.id = CASE
                    WHEN s.mentor_id = :viewerId THEN s.mentee_id
                    ELSE s.mentor_id
                END
                WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                  AND s.scheduled_start >= now()
                  AND COALESCE(UPPER(s.status::text), '') IN ('PENDING', 'CONFIRMED', 'SCHEDULED', 'IN_PROGRESS')
                  AND (
                    participant.id IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = participant.id)
                           OR (b.blocker_profile_id = participant.id AND b.blocked_profile_id = :viewerId)
                    )
                  )
                ORDER BY s.scheduled_start ASC
                LIMIT 1
                """;

        List<CommunityHomeSession> sessions = jdbc.query(
                sql,
                new MapSqlParameterSource("viewerId", viewerId),
                homeSessionRowMapper()
        );
        return sessions.stream().findFirst();
    }

    private int queryProfileCompletionPercent(UUID viewerId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT ROUND((
                    CASE WHEN NULLIF(TRIM(COALESCE(p.first_name, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.last_name, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.username, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.avatar_url, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.bio, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.industry, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.country, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.phone, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN NULLIF(TRIM(COALESCE(p.location, '')), '') IS NOT NULL THEN 1 ELSE 0 END +
                    CASE WHEN COALESCE(cardinality(p.interests), 0) > 0 THEN 1 ELSE 0 END
                ) * 100.0 / 10)::int AS profile_completion_percent
                FROM profiles p
                WHERE p.id = :viewerId
                """, new MapSqlParameterSource("viewerId", viewerId));
        return Math.max(0, Math.min(100, intValue(row, "profile_completion_percent")));
    }

    private CommunityHomeLearningSummary buildLearningSummary(
            CommunityHomeStats stats,
            CommunityHomeSession nextSession,
            int profileCompletionPercent
    ) {
        List<CommunityHomeAssignment> assignments = List.of(
                new CommunityHomeAssignment(
                        "complete-profile",
                        "Complete your profile",
                        profileCompletionPercent >= 80,
                        "/profile"
                ),
                new CommunityHomeAssignment(
                        "book-session",
                        "Book your next mentorship session",
                        nextSession != null,
                        "/mentors"
                ),
                new CommunityHomeAssignment(
                        "share-post",
                        "Share a community post",
                        stats.articles() > 0,
                        "/dashboard"
                )
        );
        long completedCount = assignments.stream().filter(CommunityHomeAssignment::completed).count();
        int progress = assignments.isEmpty()
                ? 0
                : (int) Math.round((completedCount * 100.0) / assignments.size());

        return new CommunityHomeLearningSummary(
                "Mentorship Essentials",
                "Profile, sessions, and community activity",
                progress,
                assignments
        );
    }

    public String normalizeFeedMode(String mode) {
        if ("ranked".equalsIgnoreCase(mode)) {
            return "ranked";
        }
        return "latest";
    }

    public String normalizeSearchType(String type) {
        String normalized = normalize(type);
        return switch (normalized) {
            case "posts", "people", "categories", "hashtags" -> normalized;
            default -> "all";
        };
    }

    public int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 20;
        }
        return Math.min(requestedLimit, 50);
    }

    private int clampHomePeopleLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 3;
        }
        return Math.min(requestedLimit, 12);
    }

    private List<CommunityPostItem> searchPosts(UUID viewerId, String query, int limit) {
        String sql = """
                %s
                FROM posts p
                JOIN profiles author ON author.id = p.user_id
                WHERE (p.content ILIKE :searchPattern
                   OR COALESCE(p.link_preview_title, '') ILIKE :searchPattern
                   OR COALESCE(p.link_preview_description, '') ILIKE :searchPattern)
                  AND (COALESCE(p.is_hidden, false) = false
                   OR p.user_id = :viewerId)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                       OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY p.created_at DESC
                LIMIT :limit
                """.formatted(legacyPostSelectColumns());

        return jdbc.query(sql, searchParameters(viewerId, query, limit), postRowMapper());
    }

    private List<CommunitySearchPersonItem> searchPeople(UUID viewerId, String query, int limit) {
        String sql = """
                SELECT
                    p.id,
                    p.first_name,
                    p.last_name,
                    p.avatar_url,
                    p.role::text AS role,
                    COALESCE(p.bio, '') AS headline,
                    p.industry,
                    p.country,
                    COALESCE(p.is_verified, false) AS is_verified,
                    CASE
                        WHEN EXISTS (
                            SELECT 1
                            FROM syncs s
                            WHERE ((s.mentor_id = :viewerId AND s.mentee_id = p.id)
                                OR (s.mentor_id = p.id AND s.mentee_id = :viewerId))
                              AND s.status = 'accepted'
                        ) THEN 'connected'
                        WHEN EXISTS (
                            SELECT 1
                            FROM syncs s
                            WHERE ((s.mentor_id = :viewerId AND s.mentee_id = p.id)
                                OR (s.mentor_id = p.id AND s.mentee_id = :viewerId))
                              AND s.status = 'pending'
                              AND s.requester_id = :viewerId
                        ) THEN 'pending_sent'
                        WHEN EXISTS (
                            SELECT 1
                            FROM syncs s
                            WHERE ((s.mentor_id = :viewerId AND s.mentee_id = p.id)
                                OR (s.mentor_id = p.id AND s.mentee_id = :viewerId))
                              AND s.status = 'pending'
                              AND s.requester_id <> :viewerId
                        ) THEN 'pending_received'
                        WHEN EXISTS (
                            SELECT 1
                            FROM follows f
                            WHERE f.follower_id = :viewerId
                              AND f.following_id = p.id
                        ) THEN 'following'
                        ELSE 'none'
                    END AS relationship_status,
                    (
                        CASE WHEN CONCAT_WS(' ', p.first_name, p.last_name) ILIKE :searchPattern THEN 40 ELSE 0 END
                        + CASE WHEN COALESCE(p.bio, '') ILIKE :searchPattern THEN 20 ELSE 0 END
                        + CASE WHEN COALESCE(p.industry, '') ILIKE :searchPattern THEN 20 ELSE 0 END
                        + CASE WHEN COALESCE(p.country, '') ILIKE :searchPattern THEN 10 ELSE 0 END
                        + CASE WHEN p.role::text ILIKE :searchPattern THEN 10 ELSE 0 END
                    ) AS search_score
                FROM profiles p
                WHERE p.id <> :viewerId
                  AND (p.first_name ILIKE :searchPattern
                   OR p.last_name ILIKE :searchPattern
                   OR COALESCE(p.bio, '') ILIKE :searchPattern
                   OR COALESCE(p.industry, '') ILIKE :searchPattern
                   OR COALESCE(p.country, '') ILIKE :searchPattern
                   OR p.role::text ILIKE :searchPattern)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                       OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY search_score DESC, p.first_name ASC, p.last_name ASC
                LIMIT :limit
                """;

        return jdbc.query(sql, searchParameters(viewerId, query, limit), searchPersonRowMapper(query));
    }

    private List<CommunityCategoryItem> searchCategories(String query, int limit) {
        String sql = """
                SELECT c.id, c.slug, c.name, c.description, c.sort_order
                FROM community_categories c
                WHERE c.is_active = true
                  AND (c.name ILIKE :searchPattern
                   OR c.slug ILIKE :searchPattern
                   OR COALESCE(c.description, '') ILIKE :searchPattern)
                ORDER BY c.sort_order ASC, c.name ASC
                LIMIT :limit
                """;

        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("searchPattern", searchPattern(query))
                .addValue("limit", limit), categoryRowMapper());
    }

    private List<CommunityHashtagItem> searchHashtags(UUID viewerId, String query, int limit) {
        String hashtagQuery = query.startsWith("#") ? query.substring(1) : query;
        String sql = """
                SELECT LOWER(matches[1]) AS tag, COUNT(DISTINCT p.id)::int AS posts_count
                FROM posts p
                CROSS JOIN LATERAL regexp_matches(p.content, '#([[:alnum:]_][[:alnum:]_-]*)', 'g') AS matches
                WHERE matches[1] ILIKE :searchPattern
                  AND (COALESCE(p.is_hidden, false) = false
                   OR p.user_id = :viewerId)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                       OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                  )
                GROUP BY LOWER(matches[1])
                ORDER BY posts_count DESC, tag ASC
                LIMIT :limit
                """;

        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("searchPattern", searchPattern(hashtagQuery))
                .addValue("limit", limit), (rs, rowNum) -> new CommunityHashtagItem(
                rs.getString("tag"),
                rs.getInt("posts_count")
        ));
    }

    private List<NetworkMember> queryRecentConnections(UUID viewerId, int limit) {
        String sql = """
                SELECT other_profile.*, s.id AS relationship_id, s.updated_at AS connected_at, 'connected' AS relationship_status
                FROM syncs s
                JOIN profiles other_profile ON other_profile.id = CASE
                    WHEN s.mentor_id = :viewerId THEN s.mentee_id
                    ELSE s.mentor_id
                END
                WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                  AND s.status = 'accepted'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = other_profile.id)
                       OR (b.blocker_profile_id = other_profile.id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY s.updated_at DESC
                LIMIT :limit
                """;

        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("limit", limit), networkMemberRowMapper());
    }

    private boolean shouldSearch(String normalizedType, String requestedType) {
        return "all".equals(normalizedType) || requestedType.equals(normalizedType);
    }

    private MapSqlParameterSource searchParameters(UUID viewerId, String query, int limit) {
        return new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("searchPattern", searchPattern(query))
                .addValue("limit", limit);
    }

    private String searchPattern(String query) {
        return "%" + query + "%";
    }

    private String legacyPostSelectColumns() {
        return """
                SELECT
                    p.id,
                    p.user_id,
                    p.content,
                    p.media_url,
                    p.media_type,
                    p.image_url,
                    p.link_preview_url AS link_url,
                    p.link_preview_title AS link_title,
                    p.link_preview_description AS link_description,
                    p.link_preview_image AS link_image,
                    COALESCE(p.likes_count, 0) AS likes_count,
                    COALESCE(p.comments_count, 0) AS comments_count,
                    p.created_at,
                    author.id AS author_id,
                    author.first_name,
                    author.last_name,
                    author.avatar_url,
                    author.role::text AS role,
                    COALESCE(author.bio, '') AS headline,
                    author.industry,
                    author.country,
                    COALESCE(author.is_verified, false) AS is_verified,
                    p.is_hidden AS is_hidden,
                    EXISTS (
                        SELECT 1
                        FROM post_likes pl
                        WHERE pl.post_id = p.id
                          AND pl.user_id = :viewerId
                    ) AS reacted_by_viewer,
                    EXISTS (
                        SELECT 1
                        FROM saved_posts sp
                        WHERE sp.post_id = p.id
                          AND sp.user_id = :viewerId
                    ) AS saved_by_viewer,
                    p.link_preview_domain,
                    p.link_preview_site_name,
                    COALESCE((
                        SELECT COUNT(*)
                        FROM post_impressions pi
                        WHERE pi.post_id = p.id
                    ), 0)::int AS impressions_count
                """;
    }

    public int calculateRecommendationScore(Map<String, Object> viewer, Map<String, Object> candidate) {
        int score = 0;

        if (sameText(viewer.get("industry"), candidate.get("industry"))) {
            score += 30;
        }
        if (sameText(viewer.get("country"), candidate.get("country"))) {
            score += 20;
        }
        if (isComplementaryRole(viewer.get("role"), candidate.get("role"))) {
            score += 30;
        }

        List<String> viewerInterests = toStringList(viewer.get("interests"));
        List<String> candidateInterests = toStringList(candidate.get("interests"));
        List<String> normalizedCandidateInterests = candidateInterests.stream()
                .map(this::normalize)
                .toList();

        long overlap = viewerInterests.stream()
                .map(this::normalize)
                .filter(normalizedCandidateInterests::contains)
                .count();
        score += (int) Math.min(overlap * 10, 20);

        return score;
    }

    private RecommendedPerson toRecommendedPerson(Map<String, Object> viewer, Map<String, Object> candidate) {
        int score = calculateRecommendationScore(viewer, candidate);
        List<RecommendationReason> reasons = new ArrayList<>();

        if (sameText(viewer.get("industry"), candidate.get("industry"))) {
            reasons.add(new RecommendationReason("shared_industry", "Same industry"));
        }
        if (sameText(viewer.get("country"), candidate.get("country"))) {
            reasons.add(new RecommendationReason("shared_country", "Same country"));
        }
        if (isComplementaryRole(viewer.get("role"), candidate.get("role"))) {
            reasons.add(new RecommendationReason("role_fit", "Mentorship fit"));
        }

        return new RecommendedPerson(profileFromMap(candidate), score, reasons);
    }

    private List<CommunityProfileViewItem> queryProfileViews(UUID viewerId, UUID profileId, int limit) {
        String sql = """
                SELECT
                    pv.id,
                    pv.viewer_id,
                    COALESCE(pv.viewed_at, pv.created_at) AS viewed_at,
                    viewer.id AS viewer_profile_id,
                    viewer.first_name AS viewer_first_name,
                    viewer.last_name AS viewer_last_name,
                    viewer.avatar_url AS viewer_avatar_url,
                    viewer.role::text AS viewer_role,
                    COALESCE(viewer.bio, '') AS viewer_headline,
                    viewer.industry AS viewer_industry,
                    viewer.country AS viewer_country,
                    COALESCE(viewer.is_verified, false) AS viewer_is_verified
                FROM profile_views pv
                LEFT JOIN profiles viewer ON viewer.id = pv.viewer_id
                WHERE pv.viewed_profile_id = :profileId
                  AND pv.viewer_id IS DISTINCT FROM :profileId
                  AND (
                    pv.viewer_id IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = pv.viewer_id)
                           OR (b.blocker_profile_id = pv.viewer_id AND b.blocked_profile_id = :viewerId)
                    )
                  )
                ORDER BY COALESCE(pv.viewed_at, pv.created_at) DESC
                LIMIT :limit
                """;

        return jdbc.query(sql, profileAnalyticsParameters(viewerId, profileId)
                .addValue("limit", limit), profileViewRowMapper());
    }

    private Map<String, Object> queryProfileViewStats(
            UUID viewerId,
            UUID profileId,
            OffsetDateTime oneWeekAgo,
            OffsetDateTime previousWeekStart,
            OffsetDateTime startThisMonth,
            OffsetDateTime oneMonthAgo,
            OffsetDateTime previousMonthStart
    ) {
        String sql = """
                SELECT
                    COUNT(*)::int AS total_views,
                    COUNT(*) FILTER (
                        WHERE COALESCE(pv.viewed_at, pv.created_at) >= :oneWeekAgo
                    )::int AS views_this_week,
                    COUNT(*) FILTER (
                        WHERE COALESCE(pv.viewed_at, pv.created_at) >= :startThisMonth
                    )::int AS views_this_month,
                    COUNT(*) FILTER (
                        WHERE COALESCE(pv.viewed_at, pv.created_at) >= :previousWeekStart
                          AND COALESCE(pv.viewed_at, pv.created_at) < :oneWeekAgo
                    )::int AS views_previous_week,
                    COUNT(*) FILTER (
                        WHERE COALESCE(pv.viewed_at, pv.created_at) >= :previousMonthStart
                          AND COALESCE(pv.viewed_at, pv.created_at) < :oneMonthAgo
                    )::int AS views_previous_month
                FROM profile_views pv
                WHERE pv.viewed_profile_id = :profileId
                  AND pv.viewer_id IS DISTINCT FROM :profileId
                  AND (
                    pv.viewer_id IS NULL
                    OR NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = pv.viewer_id)
                           OR (b.blocker_profile_id = pv.viewer_id AND b.blocked_profile_id = :viewerId)
                    )
                  )
                """;

        return jdbc.queryForMap(sql, profileAnalyticsParameters(viewerId, profileId)
                .addValue("oneWeekAgo", oneWeekAgo)
                .addValue("previousWeekStart", previousWeekStart)
                .addValue("startThisMonth", startThisMonth)
                .addValue("oneMonthAgo", oneMonthAgo)
                .addValue("previousMonthStart", previousMonthStart));
    }

    private int queryPostImpressionsThisMonth(UUID profileId, OffsetDateTime startThisMonth) {
        String sql = """
                SELECT COUNT(*)::int
                FROM post_impressions pi
                JOIN posts p ON p.id = pi.post_id
                WHERE p.user_id = :profileId
                  AND COALESCE(pi.created_at, pi.viewed_at) >= :startThisMonth
                """;

        Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("startThisMonth", startThisMonth), Integer.class);
        return count == null ? 0 : count;
    }

    private MapSqlParameterSource profileAnalyticsParameters(UUID viewerId, UUID profileId) {
        return new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("profileId", profileId);
    }

    private List<CommunityConnectionRequestItem> queryConnectionRequests(UUID viewerId) {
        String sql = """
                SELECT
                    s.id AS relationship_id,
                    s.mentor_id,
                    s.mentee_id,
                    s.requester_id,
                    s.status::text AS status,
                    s.created_at,
                    s.updated_at,
                    mentor.id AS mentor_profile_id,
                    mentor.first_name AS mentor_first_name,
                    mentor.last_name AS mentor_last_name,
                    mentor.avatar_url AS mentor_avatar_url,
                    mentor.email AS mentor_email,
                    mentor.role::text AS mentor_role,
                    COALESCE(mentor.bio, '') AS mentor_headline,
                    mentor.industry AS mentor_industry,
                    mentor.country AS mentor_country,
                    COALESCE(mentor.is_verified, false) AS mentor_is_verified,
                    mentee.id AS mentee_profile_id,
                    mentee.first_name AS mentee_first_name,
                    mentee.last_name AS mentee_last_name,
                    mentee.avatar_url AS mentee_avatar_url,
                    mentee.email AS mentee_email,
                    mentee.role::text AS mentee_role,
                    COALESCE(mentee.bio, '') AS mentee_headline,
                    mentee.industry AS mentee_industry,
                    mentee.country AS mentee_country,
                    COALESCE(mentee.is_verified, false) AS mentee_is_verified
                FROM syncs s
                JOIN profiles mentor ON mentor.id = s.mentor_id
                JOIN profiles mentee ON mentee.id = s.mentee_id
                WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                  AND s.status IN ('pending', 'accepted', 'rejected')
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (
                        b.blocker_profile_id = :viewerId
                        AND b.blocked_profile_id = CASE
                            WHEN s.mentor_id = :viewerId THEN s.mentee_id
                            ELSE s.mentor_id
                        END
                    )
                    OR (
                        b.blocker_profile_id = CASE
                            WHEN s.mentor_id = :viewerId THEN s.mentee_id
                            ELSE s.mentor_id
                        END
                        AND b.blocked_profile_id = :viewerId
                    )
                  )
                ORDER BY s.created_at DESC
                """;

        return jdbc.query(
                sql,
                new MapSqlParameterSource("viewerId", viewerId),
                connectionRequestRowMapper()
        );
    }

    private List<NetworkMember> queryNetworkMembers(UUID viewerId, String view) {
        return queryNetworkMembers(viewerId, viewerId, view);
    }

    private List<NetworkMember> queryNetworkMembers(UUID viewerId, UUID subjectId, String view) {
        String sql = switch (view) {
            case "connections" -> """
                    SELECT other_profile.*, s.id AS relationship_id, s.updated_at AS connected_at, 'connected' AS relationship_status
                    FROM syncs s
                    JOIN profiles other_profile ON other_profile.id = CASE
                        WHEN s.mentor_id = :subjectId THEN s.mentee_id
                        ELSE s.mentor_id
                    END
                    WHERE (s.mentor_id = :subjectId OR s.mentee_id = :subjectId)
                      AND s.status = 'accepted'
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = other_profile.id)
                           OR (b.blocker_profile_id = other_profile.id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY s.updated_at DESC
                    """;
            case "followers" -> """
                    SELECT p.*, f.id AS relationship_id, f.created_at AS connected_at, 'follower' AS relationship_status
                    FROM follows f
                    JOIN profiles p ON p.id = f.follower_id
                    WHERE f.following_id = :subjectId
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                           OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY f.created_at DESC
                    """;
            case "following" -> """
                    SELECT p.*, f.id AS relationship_id, f.created_at AS connected_at, 'following' AS relationship_status
                    FROM follows f
                    JOIN profiles p ON p.id = f.following_id
                    WHERE f.follower_id = :subjectId
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                           OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY f.created_at DESC
                    """;
            case "pendingRequests" -> """
                    SELECT p.*, s.id AS relationship_id, s.created_at AS connected_at, 'pending_received' AS relationship_status
                    FROM syncs s
                    JOIN profiles p ON p.id = CASE
                        WHEN s.mentor_id = :subjectId THEN s.mentee_id
                        ELSE s.mentor_id
                    END
                    WHERE (s.mentor_id = :subjectId OR s.mentee_id = :subjectId)
                      AND s.status = 'pending'
                      AND s.requester_id <> :subjectId
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                           OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY s.created_at DESC
                    """;
            case "sentRequests" -> """
                    SELECT p.*, s.id AS relationship_id, s.created_at AS connected_at, 'pending_sent' AS relationship_status
                    FROM syncs s
                    JOIN profiles p ON p.id = CASE
                        WHEN s.mentor_id = :subjectId THEN s.mentee_id
                        ELSE s.mentor_id
                    END
                    WHERE s.requester_id = :subjectId
                      AND s.status = 'pending'
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.id)
                           OR (b.blocker_profile_id = p.id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY s.created_at DESC
                    """;
            default -> throw new IllegalArgumentException("Unsupported network view: " + view);
        };

        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("subjectId", subjectId), networkMemberRowMapper());
    }

    private List<NetworkMember> reciprocalFollows(List<NetworkMember> followers, List<NetworkMember> following) {
        List<UUID> followerIds = followers.stream()
                .map(member -> member.profile().id())
                .toList();

        return following.stream()
                .filter(member -> followerIds.contains(member.profile().id()))
                .map(member -> new NetworkMember(
                        member.relationshipId(),
                        member.profile(),
                        "mutual",
                        member.connectedAt()
                ))
                .toList();
    }

    @SafeVarargs
    private final int uniqueNetworkCount(List<NetworkMember>... memberGroups) {
        List<UUID> profileIds = new ArrayList<>();
        for (List<NetworkMember> members : memberGroups) {
            for (NetworkMember member : members) {
                UUID profileId = member.profile().id();
                if (!profileIds.contains(profileId)) {
                    profileIds.add(profileId);
                }
            }
        }
        return profileIds.size();
    }

    private RowMapper<CommunityPostItem> postRowMapper() {
        return (rs, rowNum) -> new CommunityPostItem(
                uuid(rs, "id"),
                uuid(rs, "user_id"),
                rs.getString("content"),
                rs.getString("media_url"),
                rs.getString("media_type"),
                rs.getString("image_url"),
                rs.getString("link_url"),
                rs.getString("link_title"),
                rs.getString("link_description"),
                rs.getString("link_image"),
                rs.getInt("likes_count"),
                rs.getInt("comments_count"),
                rs.getObject("created_at", OffsetDateTime.class),
                new CommunityProfileSummary(
                        uuid(rs, "author_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("avatar_url"),
                        rs.getString("role"),
                        rs.getString("headline"),
                        rs.getString("industry"),
                        rs.getString("country"),
                        rs.getBoolean("is_verified")
                ),
                rs.getBoolean("is_hidden"),
                rs.getBoolean("reacted_by_viewer"),
                rs.getBoolean("saved_by_viewer"),
                rs.getString("link_preview_domain"),
                rs.getString("link_preview_site_name"),
                rs.getInt("impressions_count")
        );
    }

    private RowMapper<CommunityHomeSession> homeSessionRowMapper() {
        return (rs, rowNum) -> {
            UUID participantId = uuidOrNull(rs, "participant_id");
            CommunityProfileSummary participant = participantId == null ? null : new CommunityProfileSummary(
                    participantId,
                    rs.getString("participant_first_name"),
                    rs.getString("participant_last_name"),
                    rs.getString("participant_avatar_url"),
                    rs.getString("participant_role"),
                    rs.getString("participant_headline"),
                    rs.getString("participant_industry"),
                    rs.getString("participant_country"),
                    rs.getBoolean("participant_is_verified")
            );

            return new CommunityHomeSession(
                    uuid(rs, "id"),
                    rs.getString("title"),
                    rs.getObject("scheduled_start", OffsetDateTime.class),
                    rs.getObject("scheduled_end", OffsetDateTime.class),
                    rs.getString("status"),
                    rs.getString("meeting_url"),
                    participant
            );
        };
    }

    private RowMapper<NetworkMember> networkMemberRowMapper() {
        return (rs, rowNum) -> new NetworkMember(
                uuid(rs, "relationship_id"),
                new CommunityProfileSummary(
                        uuid(rs, "id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("avatar_url"),
                        rs.getString("role"),
                        rs.getString("bio"),
                        rs.getString("industry"),
                        rs.getString("country"),
                        rs.getBoolean("is_verified")
                ),
                rs.getString("relationship_status"),
                rs.getObject("connected_at", OffsetDateTime.class)
        );
    }

    private RowMapper<CommunityConnectionRequestItem> connectionRequestRowMapper() {
        return (rs, rowNum) -> new CommunityConnectionRequestItem(
                uuid(rs, "relationship_id"),
                uuid(rs, "mentor_id"),
                uuid(rs, "mentee_id"),
                uuid(rs, "requester_id"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                connectionProfile(rs, "mentor"),
                connectionProfile(rs, "mentee")
        );
    }

    private RowMapper<CommunityProfileViewItem> profileViewRowMapper() {
        return (rs, rowNum) -> {
            UUID viewerProfileId = uuidOrNull(rs, "viewer_profile_id");
            CommunityProfileSummary viewer = viewerProfileId == null ? null : new CommunityProfileSummary(
                    viewerProfileId,
                    rs.getString("viewer_first_name"),
                    rs.getString("viewer_last_name"),
                    rs.getString("viewer_avatar_url"),
                    rs.getString("viewer_role"),
                    rs.getString("viewer_headline"),
                    rs.getString("viewer_industry"),
                    rs.getString("viewer_country"),
                    rs.getBoolean("viewer_is_verified")
            );

            return new CommunityProfileViewItem(
                    uuid(rs, "id"),
                    uuidOrNull(rs, "viewer_id"),
                    rs.getObject("viewed_at", OffsetDateTime.class),
                    viewer
            );
        };
    }

    private CommunityConnectionProfile connectionProfile(ResultSet rs, String prefix) throws SQLException {
        return new CommunityConnectionProfile(
                uuid(rs, prefix + "_profile_id"),
                rs.getString(prefix + "_first_name"),
                rs.getString(prefix + "_last_name"),
                rs.getString(prefix + "_avatar_url"),
                rs.getString(prefix + "_email"),
                rs.getString(prefix + "_role"),
                rs.getString(prefix + "_headline"),
                rs.getString(prefix + "_industry"),
                rs.getString(prefix + "_country"),
                rs.getBoolean(prefix + "_is_verified")
        );
    }

    private RowMapper<CommunityCategoryItem> categoryRowMapper() {
        return (rs, rowNum) -> new CommunityCategoryItem(
                uuid(rs, "id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("sort_order")
        );
    }

    private RowMapper<CommunitySearchPersonItem> searchPersonRowMapper(String query) {
        return (rs, rowNum) -> {
            CommunityProfileSummary profile = new CommunityProfileSummary(
                    uuid(rs, "id"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("avatar_url"),
                    rs.getString("role"),
                    rs.getString("headline"),
                    rs.getString("industry"),
                    rs.getString("country"),
                    rs.getBoolean("is_verified")
            );
            return new CommunitySearchPersonItem(
                    profile,
                    rs.getString("relationship_status"),
                    rs.getInt("search_score"),
                    searchReasons(profile, query)
            );
        };
    }

    private List<RecommendationReason> searchReasons(CommunityProfileSummary profile, String query) {
        List<RecommendationReason> reasons = new ArrayList<>();
        if (containsText(profile.firstName(), query) || containsText(profile.lastName(), query)) {
            reasons.add(new RecommendationReason("name_match", "Name match"));
        }
        if (containsText(profile.headline(), query)) {
            reasons.add(new RecommendationReason("headline_match", "Profile match"));
        }
        if (containsText(profile.industry(), query)) {
            reasons.add(new RecommendationReason("industry_match", "Industry match"));
        }
        if (containsText(profile.country(), query)) {
            reasons.add(new RecommendationReason("country_match", "Country match"));
        }
        if (containsText(profile.role(), query)) {
            reasons.add(new RecommendationReason("role_match", "Role match"));
        }
        if (reasons.isEmpty()) {
            reasons.add(new RecommendationReason("profile_match", "Profile match"));
        }
        return reasons;
    }

    private CommunityProfileSummary profileFromMap(Map<String, Object> row) {
        return new CommunityProfileSummary(
                (UUID) row.get("id"),
                (String) row.get("first_name"),
                (String) row.get("last_name"),
                (String) row.get("avatar_url"),
                Objects.toString(row.get("role"), null),
                Objects.toString(row.get("headline"), ""),
                Objects.toString(row.get("industry"), null),
                Objects.toString(row.get("country"), null),
                Boolean.TRUE.equals(row.get("is_verified"))
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private int intValue(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private double growthPercentage(int current, int previous) {
        if (previous > 0) {
            return ((current - previous) / (double) previous) * 100.0;
        }
        return current > 0 ? 100.0 : 0.0;
    }

    private void requireViewer(UUID viewerId) {
        if (viewerId == null) {
            throw new IllegalArgumentException("Authenticated profile is required");
        }
    }

    private void requireId(UUID id, String message) {
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private String requireSearchQuery(String query) {
        String normalized = Objects.toString(query, "").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        return normalized;
    }

    private boolean sameText(Object left, Object right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isBlank() && a.equals(b);
    }

    private boolean containsText(String value, String query) {
        return normalize(value).contains(normalize(query));
    }

    private boolean isComplementaryRole(Object viewerRole, Object candidateRole) {
        String viewer = normalize(viewerRole);
        String candidate = normalize(candidateRole);
        return ("mentee".equals(viewer) && "mentor".equals(candidate))
                || ("mentor".equals(viewer) && "mentee".equals(candidate));
    }

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Array sqlArray) {
            try {
                Object arrayValue = sqlArray.getArray();
                if (arrayValue instanceof String[] strings) {
                    return List.of(strings);
                }
                if (arrayValue instanceof Object[] objects) {
                    return Arrays.stream(objects).map(String::valueOf).toList();
                }
            } catch (SQLException ignored) {
                return List.of();
            }
        }
        return List.of();
    }
}
