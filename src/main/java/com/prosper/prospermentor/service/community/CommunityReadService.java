package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFeedResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityMyPostsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileSummary;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySavedPostsResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.NetworkMember;
import com.prosper.prospermentor.dto.community.CommunityDtos.NetworkOverviewResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendationReason;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendedPeopleResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.RecommendedPerson;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityReadService {
    private final NamedParameterJdbcTemplate jdbc;

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
        int limit = clampLimit(requestedLimit);

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
                """, new MapSqlParameterSource("viewerId", viewerId));

        List<RecommendedPerson> people = candidates.stream()
                .map(candidate -> toRecommendedPerson(viewer, candidate))
                .filter(person -> person.score() > 0)
                .sorted(Comparator.comparingInt(RecommendedPerson::score).reversed())
                .limit(limit)
                .toList();

        return new RecommendedPeopleResponse(people, limit);
    }

    @Transactional(readOnly = true)
    public NetworkOverviewResponse getNetworkOverview(UUID viewerId) {
        return new NetworkOverviewResponse(
                queryNetworkMembers(viewerId, "connections"),
                queryNetworkMembers(viewerId, "followers"),
                queryNetworkMembers(viewerId, "following"),
                queryNetworkMembers(viewerId, "pendingRequests"),
                queryNetworkMembers(viewerId, "sentRequests")
        );
    }

    public String normalizeFeedMode(String mode) {
        if ("ranked".equalsIgnoreCase(mode)) {
            return "ranked";
        }
        return "latest";
    }

    public int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 20;
        }
        return Math.min(requestedLimit, 50);
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

    private List<NetworkMember> queryNetworkMembers(UUID viewerId, String view) {
        String sql = switch (view) {
            case "connections" -> """
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
                    """;
            case "followers" -> """
                    SELECT p.*, f.id AS relationship_id, f.created_at AS connected_at, 'follower' AS relationship_status
                    FROM follows f
                    JOIN profiles p ON p.id = f.follower_id
                    WHERE f.following_id = :viewerId
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
                    WHERE f.follower_id = :viewerId
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
                        WHEN s.mentor_id = :viewerId THEN s.mentee_id
                        ELSE s.mentor_id
                    END
                    WHERE (s.mentor_id = :viewerId OR s.mentee_id = :viewerId)
                      AND s.status = 'pending'
                      AND s.requester_id <> :viewerId
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
                        WHEN s.mentor_id = :viewerId THEN s.mentee_id
                        ELSE s.mentor_id
                    END
                    WHERE s.requester_id = :viewerId
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

        return jdbc.query(sql, new MapSqlParameterSource("viewerId", viewerId), networkMemberRowMapper());
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

    private boolean sameText(Object left, Object right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isBlank() && a.equals(b);
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
