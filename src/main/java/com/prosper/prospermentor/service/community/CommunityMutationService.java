package com.prosper.prospermentor.service.community;

import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityBlockRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityBlockResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCategoryItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentItem;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentReactionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityCommentRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityConnectionStatusRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityFollowResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesDto;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityNotificationPreferencesRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostHiddenResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostMutationResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityPostRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileSummary;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileViewTrackRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityProfileViewTrackResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReactionResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReportRequest;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunityReportResponse;
import com.prosper.prospermentor.dto.community.CommunityDtos.CommunitySavedPostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommunityMutationService {
    private static final String DEFAULT_REACTION_TYPE = "LIKE";
    private static final String DEFAULT_VISIBILITY = "PUBLIC";
    private static final String DEFAULT_DIGEST_FREQUENCY = "DAILY";

    private enum PostStorage {
        COMMUNITY,
        LEGACY
    }

    private record ConnectionRow(
            UUID id,
            UUID mentorId,
            UUID menteeId,
            UUID requesterId,
            String status
    ) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final CommunityEventOutboxService outboxService;

    @Transactional(readOnly = true)
    public List<CommunityCategoryItem> getCategories() {
        return jdbc.query("""
                SELECT id, slug, name, description, sort_order
                FROM community_categories
                WHERE is_active = true
                ORDER BY sort_order ASC, name ASC
                """, new MapSqlParameterSource(), categoryMapper());
    }

    @Transactional
    public CommunityPostMutationResponse createPost(UUID viewerId, CommunityPostRequest request) {
        requireViewer(viewerId);
        if (request == null) {
            throw new IllegalArgumentException("Post request is required");
        }

        String content = requireText(request.content(), "Post content is required");
        String visibility = normalizeVisibility(request.visibility());
        UUID categoryId = request.categoryId();
        ensureCategoryIsActive(categoryId);

        UUID postId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        List<String> hashtags = normalizeHashtags(request.hashtags());

        jdbc.update("""
                INSERT INTO posts (
                    id,
                    user_id,
                    content,
                    media_url,
                    media_type,
                    image_url,
                    link_preview_url,
                    link_preview_title,
                    link_preview_description,
                    link_preview_image,
                    link_preview_domain,
                    link_preview_site_name,
                    likes_count,
                    comments_count,
                    is_hidden,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :authorProfileId,
                    :content,
                    :mediaUrl,
                    :mediaType,
                    :imageUrl,
                    :linkUrl,
                    :linkTitle,
                    :linkDescription,
                    :linkImage,
                    NULL,
                    NULL,
                    0,
                    0,
                    false,
                    now(),
                    now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", postId)
                .addValue("authorProfileId", viewerId)
                .addValue("content", content)
                .addValue("mediaUrl", trimmed(request.mediaUrl()))
                .addValue("mediaType", trimmed(request.mediaType()))
                .addValue("imageUrl", trimmed(request.imageUrl()))
                .addValue("linkUrl", trimmed(request.linkUrl()))
                .addValue("linkTitle", trimmed(request.linkTitle()))
                .addValue("linkDescription", trimmed(request.linkDescription()))
                .addValue("linkImage", trimmed(request.linkImage())));

        outboxService.recordEvent(
                "COMMUNITY_POST_CREATED",
                "POST",
                postId,
                viewerId,
                null,
                Map.of("postId", postId, "visibility", visibility)
        );

        return new CommunityPostMutationResponse(
                postId,
                viewerId,
                categoryId,
                content,
                visibility,
                "ACTIVE",
                "APPROVED",
                trimmed(request.mediaUrl()),
                trimmed(request.mediaType()),
                trimmed(request.imageUrl()),
                trimmed(request.linkUrl()),
                trimmed(request.linkTitle()),
                trimmed(request.linkDescription()),
                trimmed(request.linkImage()),
                hashtags,
                0,
                0,
                0,
                0,
                now,
                now
        );
    }

    @Transactional
    public CommunityPostMutationResponse updatePost(UUID viewerId, UUID postId, CommunityPostRequest request) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        if (request == null) {
            throw new IllegalArgumentException("Post request is required");
        }

        String content = requireText(request.content(), "Post content is required");
        String visibility = normalizeVisibility(request.visibility());
        UUID categoryId = request.categoryId();
        ensureCategoryIsActive(categoryId);
        List<String> hashtags = normalizeHashtags(request.hashtags());

        PostStorage storage = resolveOwnedPostStorage(viewerId, postId);
        if (storage == PostStorage.LEGACY) {
            jdbc.update("""
                    UPDATE posts
                    SET content = :content,
                        media_url = :mediaUrl,
                        media_type = :mediaType,
                        image_url = :imageUrl,
                        link_preview_url = :linkUrl,
                        link_preview_title = :linkTitle,
                        link_preview_description = :linkDescription,
                        link_preview_image = :linkImage,
                        updated_at = now()
                    WHERE id = :postId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId)
                    .addValue("content", content)
                    .addValue("mediaUrl", trimmed(request.mediaUrl()))
                    .addValue("mediaType", trimmed(request.mediaType()))
                    .addValue("imageUrl", trimmed(request.imageUrl()))
                    .addValue("linkUrl", trimmed(request.linkUrl()))
                    .addValue("linkTitle", trimmed(request.linkTitle()))
                    .addValue("linkDescription", trimmed(request.linkDescription()))
                    .addValue("linkImage", trimmed(request.linkImage())));

            outboxService.recordEvent(
                    "COMMUNITY_POST_UPDATED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId, "visibility", visibility)
            );

            return new CommunityPostMutationResponse(
                    postId,
                    viewerId,
                    categoryId,
                    content,
                    visibility,
                    "ACTIVE",
                    "APPROVED",
                    trimmed(request.mediaUrl()),
                    trimmed(request.mediaType()),
                    trimmed(request.imageUrl()),
                    trimmed(request.linkUrl()),
                    trimmed(request.linkTitle()),
                    trimmed(request.linkDescription()),
                    trimmed(request.linkImage()),
                    hashtags,
                    0,
                    0,
                    0,
                    0,
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            );
        }

        jdbc.update("""
                UPDATE community_posts
                SET category_id = :categoryId,
                    content = :content,
                    visibility = :visibility,
                    media_url = :mediaUrl,
                    media_type = :mediaType,
                    image_url = :imageUrl,
                    link_url = :linkUrl,
                    link_title = :linkTitle,
                    link_description = :linkDescription,
                    link_image = :linkImage,
                    hashtags = CAST(:hashtags AS text[]),
                    updated_at = now()
                WHERE id = :postId
                  AND author_profile_id = :viewerId
                  AND status <> 'DELETED'
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("viewerId", viewerId)
                .addValue("categoryId", categoryId)
                .addValue("content", content)
                .addValue("visibility", visibility)
                .addValue("mediaUrl", trimmed(request.mediaUrl()))
                .addValue("mediaType", trimmed(request.mediaType()))
                .addValue("imageUrl", trimmed(request.imageUrl()))
                .addValue("linkUrl", trimmed(request.linkUrl()))
                .addValue("linkTitle", trimmed(request.linkTitle()))
                .addValue("linkDescription", trimmed(request.linkDescription()))
                .addValue("linkImage", trimmed(request.linkImage()))
                .addValue("hashtags", hashtags.toArray(String[]::new)));

        outboxService.recordEvent(
                "COMMUNITY_POST_UPDATED",
                "POST",
                postId,
                viewerId,
                null,
                Map.of("postId", postId, "visibility", visibility)
        );

        return loadPost(postId);
    }

    @Transactional
    public Map<String, Object> deletePost(UUID viewerId, UUID postId) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveOwnedPostStorage(viewerId, postId);

        if (storage == PostStorage.LEGACY) {
            jdbc.update("""
                    DELETE FROM posts
                    WHERE id = :postId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId));

            outboxService.recordEvent(
                    "COMMUNITY_POST_DELETED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId)
            );

            return Map.of("postId", postId, "deleted", true);
        }

        jdbc.update("""
                UPDATE community_posts
                SET status = 'DELETED',
                    deleted_at = COALESCE(deleted_at, now()),
                    updated_at = now()
                WHERE id = :postId
                  AND author_profile_id = :viewerId
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("viewerId", viewerId));

        outboxService.recordEvent(
                "COMMUNITY_POST_DELETED",
                "POST",
                postId,
                viewerId,
                null,
                Map.of("postId", postId)
        );

        return Map.of("postId", postId, "deleted", true);
    }

    @Transactional
    public CommunityPostHiddenResponse setPostHidden(UUID viewerId, UUID postId, CommunityPostHiddenRequest request) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        boolean hidden = request != null && Boolean.TRUE.equals(request.hidden());
        ensureLegacyPostOwner(viewerId, postId);

        jdbc.update("""
                UPDATE posts
                SET is_hidden = :hidden,
                    updated_at = now()
                WHERE id = :postId
                  AND user_id = :viewerId
                """, new MapSqlParameterSource()
                .addValue("hidden", hidden)
                .addValue("postId", postId)
                .addValue("viewerId", viewerId));

        outboxService.recordEvent(
                hidden ? "COMMUNITY_POST_HIDDEN" : "COMMUNITY_POST_UNHIDDEN",
                "POST",
                postId,
                viewerId,
                null,
                Map.of("postId", postId, "hidden", hidden)
        );

        return new CommunityPostHiddenResponse(postId, hidden);
    }

    @Transactional
    public CommunityReactionResponse reactToPost(UUID viewerId, UUID postId, CommunityReactionRequest request) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveVisiblePostStorage(viewerId, postId);
        String reactionType = normalizeReactionType(request == null ? null : request.reactionType());

        if (storage == PostStorage.LEGACY) {
            int inserted = jdbc.update("""
                    INSERT INTO post_likes (
                        id,
                        post_id,
                        user_id,
                        created_at
                    )
                    VALUES (:id, :postId, :viewerId, now())
                    ON CONFLICT (post_id, user_id) DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId));

            if (inserted > 0) {
                jdbc.update("""
                        UPDATE posts
                        SET likes_count = COALESCE(likes_count, 0) + 1,
                            updated_at = now()
                        WHERE id = :postId
                        """, new MapSqlParameterSource("postId", postId));
                outboxService.recordEvent(
                        "COMMUNITY_POST_REACTED",
                        "POST",
                        postId,
                        viewerId,
                        null,
                        Map.of("postId", postId, "reactionType", reactionType)
                );
            }

            return new CommunityReactionResponse(postId, reactionType, true, countLegacyPostLikes(postId));
        }

        int inserted = jdbc.update("""
                INSERT INTO community_post_reactions (
                    id,
                    post_id,
                    user_profile_id,
                    reaction_type,
                    created_at
                )
                VALUES (:id, :postId, :viewerId, :reactionType, now())
                ON CONFLICT (post_id, user_profile_id, reaction_type) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("postId", postId)
                .addValue("viewerId", viewerId)
                .addValue("reactionType", reactionType));

        if (inserted > 0) {
            jdbc.update("""
                    UPDATE community_posts
                    SET likes_count = likes_count + 1,
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", postId));
            outboxService.recordEvent(
                    "COMMUNITY_POST_REACTED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId, "reactionType", reactionType)
            );
        }

        return new CommunityReactionResponse(postId, reactionType, true, countPostReactions(postId, reactionType));
    }

    @Transactional
    public CommunityReactionResponse removeReaction(UUID viewerId, UUID postId, String reactionTypeInput) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveVisiblePostStorage(viewerId, postId);
        String reactionType = normalizeReactionType(reactionTypeInput);

        if (storage == PostStorage.LEGACY) {
            int deleted = jdbc.update("""
                    DELETE FROM post_likes
                    WHERE post_id = :postId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId));

            if (deleted > 0) {
                jdbc.update("""
                        UPDATE posts
                        SET likes_count = GREATEST(COALESCE(likes_count, 0) - 1, 0),
                            updated_at = now()
                        WHERE id = :postId
                        """, new MapSqlParameterSource("postId", postId));
                outboxService.recordEvent(
                        "COMMUNITY_POST_UNREACTED",
                        "POST",
                        postId,
                        viewerId,
                        null,
                        Map.of("postId", postId, "reactionType", reactionType)
                );
            }

            return new CommunityReactionResponse(postId, reactionType, false, countLegacyPostLikes(postId));
        }

        int deleted = jdbc.update("""
                DELETE FROM community_post_reactions
                WHERE post_id = :postId
                  AND user_profile_id = :viewerId
                  AND reaction_type = :reactionType
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("viewerId", viewerId)
                .addValue("reactionType", reactionType));

        if (deleted > 0) {
            jdbc.update("""
                    UPDATE community_posts
                    SET likes_count = GREATEST(likes_count - 1, 0),
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", postId));
            outboxService.recordEvent(
                    "COMMUNITY_POST_UNREACTED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId, "reactionType", reactionType)
            );
        }

        return new CommunityReactionResponse(postId, reactionType, false, countPostReactions(postId, reactionType));
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentItem> getComments(UUID viewerId, UUID postId) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveVisiblePostStorage(viewerId, postId);

        if (storage == PostStorage.LEGACY) {
            return jdbc.query("""
                    SELECT
                        c.id,
                        c.post_id,
                        c.user_id AS author_profile_id,
                        c.parent_id AS parent_comment_id,
                        c.content,
                        'ACTIVE' AS status,
                        c.created_at,
                        c.updated_at,
                        author.id AS profile_id,
                        author.first_name,
                        author.last_name,
                        author.avatar_url,
                        author.role::text AS role,
                        COALESCE(author.bio, '') AS headline,
                        author.industry,
                        author.country,
                        COALESCE(author.is_verified, false) AS is_verified
                    FROM post_comments c
                    JOIN profiles author ON author.id = c.user_id
                    WHERE c.post_id = :postId
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = c.user_id)
                           OR (b.blocker_profile_id = c.user_id AND b.blocked_profile_id = :viewerId)
                      )
                    ORDER BY c.created_at ASC
                    """, new MapSqlParameterSource()
                    .addValue("viewerId", viewerId)
                    .addValue("postId", postId), commentMapper());
        }

        return jdbc.query("""
                SELECT
                    c.id,
                    c.post_id,
                    c.author_profile_id,
                    c.parent_comment_id,
                    c.content,
                    c.status,
                    c.created_at,
                    c.updated_at,
                    author.id AS profile_id,
                    author.first_name,
                    author.last_name,
                    author.avatar_url,
                    author.role::text AS role,
                    COALESCE(author.bio, '') AS headline,
                    author.industry,
                    author.country,
                    COALESCE(author.is_verified, false) AS is_verified
                FROM community_comments c
                JOIN profiles author ON author.id = c.author_profile_id
                WHERE c.post_id = :postId
                  AND c.status = 'ACTIVE'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = c.author_profile_id)
                       OR (b.blocker_profile_id = c.author_profile_id AND b.blocked_profile_id = :viewerId)
                  )
                ORDER BY c.created_at ASC
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), commentMapper());
    }

    @Transactional
    public CommunityCommentItem createComment(UUID viewerId, UUID postId, CommunityCommentRequest request) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveVisiblePostStorage(viewerId, postId);
        if (request == null) {
            throw new IllegalArgumentException("Comment request is required");
        }
        String content = requireText(request.content(), "Comment content is required");
        UUID parentCommentId = request.parentCommentId();
        if (parentCommentId != null) {
            if (storage == PostStorage.LEGACY) {
                ensureLegacyReplyTarget(postId, parentCommentId);
            } else {
                ensureReplyTarget(postId, parentCommentId);
            }
        }

        UUID commentId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (storage == PostStorage.LEGACY) {
            jdbc.update("""
                    INSERT INTO post_comments (
                        id,
                        post_id,
                        user_id,
                        parent_id,
                        content,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :id,
                        :postId,
                        :viewerId,
                        :parentCommentId,
                        :content,
                        now(),
                        now()
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", commentId)
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId)
                    .addValue("parentCommentId", parentCommentId)
                    .addValue("content", content));

            jdbc.update("""
                    UPDATE posts
                    SET comments_count = COALESCE(comments_count, 0) + 1,
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", postId));

            outboxService.recordEvent(
                    "COMMUNITY_COMMENT_CREATED",
                    "COMMENT",
                    commentId,
                    viewerId,
                    null,
                    Map.of("postId", postId, "commentId", commentId)
            );

            return new CommunityCommentItem(commentId, postId, viewerId, parentCommentId, content, "ACTIVE", now, now, null);
        }

        jdbc.update("""
                INSERT INTO community_comments (
                    id,
                    post_id,
                    author_profile_id,
                    parent_comment_id,
                    content,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :postId,
                    :viewerId,
                    :parentCommentId,
                    :content,
                    'ACTIVE',
                    now(),
                    now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", commentId)
                .addValue("postId", postId)
                .addValue("viewerId", viewerId)
                .addValue("parentCommentId", parentCommentId)
                .addValue("content", content));

        jdbc.update("""
                UPDATE community_posts
                SET comments_count = comments_count + 1,
                    updated_at = now()
                WHERE id = :postId
                """, new MapSqlParameterSource("postId", postId));

        outboxService.recordEvent(
                "COMMUNITY_COMMENT_CREATED",
                "COMMENT",
                commentId,
                viewerId,
                null,
                Map.of("postId", postId, "commentId", commentId)
        );

        return new CommunityCommentItem(commentId, postId, viewerId, parentCommentId, content, "ACTIVE", now, now, null);
    }

    @Transactional
    public Map<String, Object> deleteComment(UUID viewerId, UUID commentId) {
        requireViewer(viewerId);
        requireId(commentId, "commentId is required");
        UUID legacyPostId = legacyCommentOwnerPostId(viewerId, commentId);
        if (legacyPostId != null) {
            jdbc.update("""
                    DELETE FROM post_comments
                    WHERE id = :commentId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("commentId", commentId)
                    .addValue("viewerId", viewerId));

            jdbc.update("""
                    UPDATE posts
                    SET comments_count = GREATEST(COALESCE(comments_count, 0) - 1, 0),
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", legacyPostId));

            outboxService.recordEvent(
                    "COMMUNITY_COMMENT_DELETED",
                    "COMMENT",
                    commentId,
                    viewerId,
                    null,
                    Map.of("postId", legacyPostId, "commentId", commentId)
            );

            return Map.of("commentId", commentId, "deleted", true);
        }

        UUID postId = requireCommentOwnerAndPost(viewerId, commentId);

        jdbc.update("""
                UPDATE community_comments
                SET status = 'DELETED',
                    deleted_at = COALESCE(deleted_at, now()),
                    updated_at = now()
                WHERE id = :commentId
                  AND author_profile_id = :viewerId
                """, new MapSqlParameterSource()
                .addValue("commentId", commentId)
                .addValue("viewerId", viewerId));

        jdbc.update("""
                UPDATE community_posts
                SET comments_count = GREATEST(comments_count - 1, 0),
                    updated_at = now()
                WHERE id = :postId
                """, new MapSqlParameterSource("postId", postId));

        outboxService.recordEvent(
                "COMMUNITY_COMMENT_DELETED",
                "COMMENT",
                commentId,
                viewerId,
                null,
                Map.of("postId", postId, "commentId", commentId)
        );

        return Map.of("commentId", commentId, "deleted", true);
    }

    @Transactional
    public CommunitySavedPostResponse savePost(UUID viewerId, UUID postId) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveVisiblePostStorage(viewerId, postId);

        if (storage == PostStorage.LEGACY) {
            int inserted = jdbc.update("""
                    INSERT INTO saved_posts (
                        id,
                        post_id,
                        user_id,
                        created_at
                    )
                    VALUES (:id, :postId, :viewerId, now())
                    ON CONFLICT (user_id, post_id) DO NOTHING
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId));

            if (inserted > 0) {
                outboxService.recordEvent(
                        "COMMUNITY_POST_SAVED",
                        "POST",
                        postId,
                        viewerId,
                        null,
                        Map.of("postId", postId)
                );
            }

            return new CommunitySavedPostResponse(postId, true, countLegacyPostSaves(postId));
        }

        int inserted = jdbc.update("""
                INSERT INTO community_saved_posts (
                    post_id,
                    user_profile_id,
                    created_at
                )
                VALUES (:postId, :viewerId, now())
                ON CONFLICT (post_id, user_profile_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("viewerId", viewerId));

        if (inserted > 0) {
            jdbc.update("""
                    UPDATE community_posts
                    SET saves_count = saves_count + 1,
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", postId));
            outboxService.recordEvent(
                    "COMMUNITY_POST_SAVED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId)
            );
        }

        return new CommunitySavedPostResponse(postId, true, getPostCounter(postId, "saves_count"));
    }

    @Transactional
    public CommunitySavedPostResponse unsavePost(UUID viewerId, UUID postId) {
        requireViewer(viewerId);
        requireId(postId, "postId is required");
        PostStorage storage = resolveExistingPostStorage(postId);

        if (storage == PostStorage.LEGACY) {
            int deleted = jdbc.update("""
                    DELETE FROM saved_posts
                    WHERE post_id = :postId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("postId", postId)
                    .addValue("viewerId", viewerId));

            if (deleted > 0) {
                outboxService.recordEvent(
                        "COMMUNITY_POST_UNSAVED",
                        "POST",
                        postId,
                        viewerId,
                        null,
                        Map.of("postId", postId)
                );
            }

            return new CommunitySavedPostResponse(postId, false, countLegacyPostSaves(postId));
        }

        int deleted = jdbc.update("""
                DELETE FROM community_saved_posts
                WHERE post_id = :postId
                  AND user_profile_id = :viewerId
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("viewerId", viewerId));

        if (deleted > 0) {
            jdbc.update("""
                    UPDATE community_posts
                    SET saves_count = GREATEST(saves_count - 1, 0),
                        updated_at = now()
                    WHERE id = :postId
                    """, new MapSqlParameterSource("postId", postId));
            outboxService.recordEvent(
                    "COMMUNITY_POST_UNSAVED",
                    "POST",
                    postId,
                    viewerId,
                    null,
                    Map.of("postId", postId)
            );
        }

        return new CommunitySavedPostResponse(postId, false, getPostCounter(postId, "saves_count"));
    }

    @Transactional
    public CommunityBlockResponse blockUser(UUID viewerId, CommunityBlockRequest request) {
        requireViewer(viewerId);
        if (request == null || request.blockedProfileId() == null) {
            throw new IllegalArgumentException("blockedProfileId is required");
        }
        UUID blockedProfileId = request.blockedProfileId();
        if (viewerId.equals(blockedProfileId)) {
            throw new IllegalArgumentException("You cannot block yourself");
        }

        int inserted = jdbc.update("""
                INSERT INTO community_blocks (
                    id,
                    blocker_profile_id,
                    blocked_profile_id,
                    reason,
                    created_at
                )
                VALUES (:id, :viewerId, :blockedProfileId, :reason, now())
                ON CONFLICT (blocker_profile_id, blocked_profile_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("viewerId", viewerId)
                .addValue("blockedProfileId", blockedProfileId)
                .addValue("reason", trimmed(request.reason())));

        if (inserted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_USER_BLOCKED",
                    "PROFILE",
                    blockedProfileId,
                    viewerId,
                    viewerId,
                    Map.of("blockedProfileId", blockedProfileId)
            );
        }

        return new CommunityBlockResponse(blockedProfileId, true);
    }

    @Transactional
    public CommunityBlockResponse unblockUser(UUID viewerId, UUID blockedProfileId) {
        requireViewer(viewerId);
        requireId(blockedProfileId, "blockedProfileId is required");

        int deleted = jdbc.update("""
                DELETE FROM community_blocks
                WHERE blocker_profile_id = :viewerId
                  AND blocked_profile_id = :blockedProfileId
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("blockedProfileId", blockedProfileId));

        if (deleted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_USER_UNBLOCKED",
                    "PROFILE",
                    blockedProfileId,
                    viewerId,
                    viewerId,
                    Map.of("blockedProfileId", blockedProfileId)
            );
        }

        return new CommunityBlockResponse(blockedProfileId, false);
    }

    @Transactional
    public CommunityFollowResponse followProfile(UUID viewerId, UUID targetProfileId) {
        requireProfileRelationship(viewerId, targetProfileId);

        int inserted = jdbc.update("""
                INSERT INTO follows (
                    id,
                    follower_id,
                    following_id,
                    created_at
                )
                VALUES (:id, :viewerId, :targetProfileId, now())
                ON CONFLICT (follower_id, following_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("viewerId", viewerId)
                .addValue("targetProfileId", targetProfileId));

        if (inserted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_PROFILE_FOLLOWED",
                    "PROFILE",
                    targetProfileId,
                    viewerId,
                    targetProfileId,
                    Map.of("targetProfileId", targetProfileId)
            );
        }

        return new CommunityFollowResponse(targetProfileId, true);
    }

    @Transactional
    public CommunityFollowResponse unfollowProfile(UUID viewerId, UUID targetProfileId) {
        requireViewer(viewerId);
        requireId(targetProfileId, "targetProfileId is required");

        int deleted = jdbc.update("""
                DELETE FROM follows
                WHERE follower_id = :viewerId
                  AND following_id = :targetProfileId
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("targetProfileId", targetProfileId));

        if (deleted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_PROFILE_UNFOLLOWED",
                    "PROFILE",
                    targetProfileId,
                    viewerId,
                    targetProfileId,
                    Map.of("targetProfileId", targetProfileId)
            );
        }

        return new CommunityFollowResponse(targetProfileId, false);
    }

    @Transactional
    public CommunityConnectionResponse requestConnection(UUID viewerId, UUID targetProfileId) {
        requireProfileRelationship(viewerId, targetProfileId);

        ConnectionRow existing = findConnectionBetween(viewerId, targetProfileId);
        if (existing != null) {
            return toConnectionResponse(viewerId, existing);
        }

        UUID relationshipId = UUID.randomUUID();
        UUID mentorId = canonicalFirst(viewerId, targetProfileId);
        UUID menteeId = viewerId.equals(mentorId) ? targetProfileId : viewerId;
        int inserted = jdbc.update("""
                INSERT INTO syncs (
                    id,
                    mentor_id,
                    mentee_id,
                    requester_id,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :mentorId,
                    :menteeId,
                    :requesterId,
                    'pending',
                    now(),
                    now()
                )
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", relationshipId)
                .addValue("mentorId", mentorId)
                .addValue("menteeId", menteeId)
                .addValue("requesterId", viewerId));
        if (inserted == 0) {
            ConnectionRow racedConnection = findConnectionBetween(viewerId, targetProfileId);
            if (racedConnection != null) {
                return toConnectionResponse(viewerId, racedConnection);
            }
            throw new IllegalStateException("Community connection request could not be created");
        }

        outboxService.recordEvent(
                "COMMUNITY_CONNECTION_REQUESTED",
                "SYNC",
                relationshipId,
                viewerId,
                targetProfileId,
                Map.of("relationshipId", relationshipId, "targetProfileId", targetProfileId)
        );

        return new CommunityConnectionResponse(relationshipId, targetProfileId, "pending_sent");
    }

    @Transactional
    public CommunityConnectionResponse updateConnectionStatus(
            UUID viewerId,
            UUID relationshipId,
            CommunityConnectionStatusRequest request
    ) {
        requireViewer(viewerId);
        requireId(relationshipId, "relationshipId is required");
        String status = normalizeConnectionDecision(request == null ? null : request.status());
        ConnectionRow connection = requireConnectionForParticipant(viewerId, relationshipId);
        if (!"pending".equalsIgnoreCase(connection.status())) {
            throw new IllegalArgumentException("Only pending connection requests can be updated");
        }
        if (viewerId.equals(connection.requesterId())) {
            throw new SecurityException("Connection request requester cannot accept or reject their own request");
        }

        jdbc.update("""
                UPDATE syncs
                SET status = :status,
                    updated_at = now()
                WHERE id = :relationshipId
                """, new MapSqlParameterSource()
                .addValue("relationshipId", relationshipId)
                .addValue("status", status));

        UUID targetProfileId = otherParticipantId(viewerId, connection);
        String eventType = "accepted".equals(status)
                ? "COMMUNITY_CONNECTION_ACCEPTED"
                : "COMMUNITY_CONNECTION_REJECTED";
        outboxService.recordEvent(
                eventType,
                "SYNC",
                relationshipId,
                viewerId,
                targetProfileId,
                Map.of("relationshipId", relationshipId, "targetProfileId", targetProfileId, "status", status)
        );

        return new CommunityConnectionResponse(
                relationshipId,
                targetProfileId,
                "accepted".equals(status) ? "connected" : "rejected"
        );
    }

    @Transactional
    public CommunityConnectionResponse cancelConnectionRequest(UUID viewerId, UUID relationshipId) {
        requireViewer(viewerId);
        requireId(relationshipId, "relationshipId is required");
        ConnectionRow connection = requireConnectionForParticipant(viewerId, relationshipId);
        if (!"pending".equalsIgnoreCase(connection.status())) {
            throw new IllegalArgumentException("Only pending connection requests can be cancelled");
        }
        if (!viewerId.equals(connection.requesterId())) {
            throw new SecurityException("Only the requester can cancel this connection request");
        }

        jdbc.update("""
                DELETE FROM syncs
                WHERE id = :relationshipId
                """, new MapSqlParameterSource("relationshipId", relationshipId));

        UUID targetProfileId = otherParticipantId(viewerId, connection);
        outboxService.recordEvent(
                "COMMUNITY_CONNECTION_CANCELLED",
                "SYNC",
                relationshipId,
                viewerId,
                targetProfileId,
                Map.of("relationshipId", relationshipId, "targetProfileId", targetProfileId)
        );

        return new CommunityConnectionResponse(relationshipId, targetProfileId, "cancelled");
    }

    @Transactional
    public CommunityReportResponse reportContent(UUID viewerId, CommunityReportRequest request) {
        requireViewer(viewerId);
        if (request == null) {
            throw new IllegalArgumentException("Report request is required");
        }
        String targetType = normalizeTargetType(request.targetType());
        UUID targetId = requireId(request.targetId(), "targetId is required");
        String reasonCode = requireText(request.reasonCode(), "reasonCode is required");
        ensureReportTargetExists(targetType, targetId);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId);
        List<CommunityReportResponse> existing = jdbc.query("""
                SELECT id, target_type, target_id, status
                FROM community_reports
                WHERE reporter_profile_id = :viewerId
                  AND target_type = :targetType
                  AND target_id = :targetId
                  AND status = 'OPEN'
                LIMIT 1
                """, parameters, reportMapper());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID reportId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO community_reports (
                    id,
                    reporter_profile_id,
                    target_type,
                    target_id,
                    reason_code,
                    reason_detail,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :viewerId,
                    :targetType,
                    :targetId,
                    :reasonCode,
                    :reasonDetail,
                    'OPEN',
                    now(),
                    now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", reportId)
                .addValue("viewerId", viewerId)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId)
                .addValue("reasonCode", reasonCode)
                .addValue("reasonDetail", trimmed(request.reasonDetail())));

        if ("POST".equals(targetType) && isCommunityPostKnown(targetId)) {
            jdbc.update("""
                    UPDATE community_posts
                    SET reports_count = reports_count + 1,
                        updated_at = now()
                    WHERE id = :targetId
                    """, new MapSqlParameterSource("targetId", targetId));
        }

        outboxService.recordEvent(
                "COMMUNITY_CONTENT_REPORTED",
                targetType,
                targetId,
                viewerId,
                null,
                Map.of("reportId", reportId, "targetType", targetType, "targetId", targetId)
        );

        return new CommunityReportResponse(reportId, targetType, targetId, "OPEN");
    }

    @Transactional
    public CommunityCommentReactionResponse reactToComment(
            UUID viewerId,
            UUID commentId,
            CommunityReactionRequest request
    ) {
        requireViewer(viewerId);
        requireId(commentId, "commentId is required");
        String reactionType = normalizeReactionType(request == null ? null : request.reactionType());
        ensureLegacyCommentVisibleForViewer(viewerId, commentId);

        int inserted = jdbc.update("""
                INSERT INTO comment_likes (
                    id,
                    comment_id,
                    user_id,
                    created_at
                )
                VALUES (:id, :commentId, :viewerId, now())
                ON CONFLICT (comment_id, user_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("commentId", commentId)
                .addValue("viewerId", viewerId));

        if (inserted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_COMMENT_REACTED",
                    "COMMENT",
                    commentId,
                    viewerId,
                    null,
                    Map.of("commentId", commentId, "reactionType", reactionType)
            );
        }

        return new CommunityCommentReactionResponse(commentId, reactionType, true, countLegacyCommentLikes(commentId));
    }

    @Transactional
    public CommunityCommentReactionResponse removeCommentReaction(UUID viewerId, UUID commentId, String reactionTypeInput) {
        requireViewer(viewerId);
        requireId(commentId, "commentId is required");
        String reactionType = normalizeReactionType(reactionTypeInput);
        ensureLegacyCommentVisibleForViewer(viewerId, commentId);

        int deleted = jdbc.update("""
                DELETE FROM comment_likes
                WHERE comment_id = :commentId
                  AND user_id = :viewerId
                """, new MapSqlParameterSource()
                .addValue("commentId", commentId)
                .addValue("viewerId", viewerId));

        if (deleted > 0) {
            outboxService.recordEvent(
                    "COMMUNITY_COMMENT_UNREACTED",
                    "COMMENT",
                    commentId,
                    viewerId,
                    null,
                    Map.of("commentId", commentId, "reactionType", reactionType)
            );
        }

        return new CommunityCommentReactionResponse(commentId, reactionType, false, countLegacyCommentLikes(commentId));
    }

    @Transactional(readOnly = true)
    public CommunityNotificationPreferencesDto getNotificationPreferences(UUID viewerId) {
        requireViewer(viewerId);
        List<CommunityNotificationPreferencesDto> rows = jdbc.query("""
                SELECT
                    profile_id,
                    in_app_enabled,
                    email_enabled,
                    whatsapp_enabled,
                    mentions_enabled,
                    comments_enabled,
                    reactions_enabled,
                    connections_enabled,
                    recommendations_enabled,
                    digest_frequency,
                    quiet_hours_start,
                    quiet_hours_end,
                    updated_at
                FROM community_notification_preferences
                WHERE profile_id = :viewerId
                """, new MapSqlParameterSource("viewerId", viewerId), notificationPreferencesMapper());

        if (rows.isEmpty()) {
            return defaultNotificationPreferences(viewerId);
        }
        return rows.get(0);
    }

    @Transactional
    public CommunityNotificationPreferencesDto updateNotificationPreferences(
            UUID viewerId,
            CommunityNotificationPreferencesRequest request
    ) {
        requireViewer(viewerId);
        if (request == null) {
            throw new IllegalArgumentException("Notification preferences request is required");
        }

        String digestFrequency = normalizeDigestFrequency(request.digestFrequency());
        LocalTime quietHoursStart = parseQuietHour(request.quietHoursStart(), "quietHoursStart");
        LocalTime quietHoursEnd = parseQuietHour(request.quietHoursEnd(), "quietHoursEnd");
        boolean inAppEnabled = defaultTrue(request.inAppEnabled());
        boolean emailEnabled = defaultTrue(request.emailEnabled());
        boolean whatsappEnabled = Boolean.TRUE.equals(request.whatsappEnabled());
        boolean mentionsEnabled = defaultTrue(request.mentionsEnabled());
        boolean commentsEnabled = defaultTrue(request.commentsEnabled());
        boolean reactionsEnabled = defaultTrue(request.reactionsEnabled());
        boolean connectionsEnabled = defaultTrue(request.connectionsEnabled());
        boolean recommendationsEnabled = defaultTrue(request.recommendationsEnabled());

        jdbc.update("""
                INSERT INTO community_notification_preferences (
                    profile_id,
                    in_app_enabled,
                    email_enabled,
                    whatsapp_enabled,
                    mentions_enabled,
                    comments_enabled,
                    reactions_enabled,
                    connections_enabled,
                    recommendations_enabled,
                    digest_frequency,
                    quiet_hours_start,
                    quiet_hours_end,
                    created_at,
                    updated_at
                )
                VALUES (
                    :viewerId,
                    :inAppEnabled,
                    :emailEnabled,
                    :whatsappEnabled,
                    :mentionsEnabled,
                    :commentsEnabled,
                    :reactionsEnabled,
                    :connectionsEnabled,
                    :recommendationsEnabled,
                    :digestFrequency,
                    :quietHoursStart,
                    :quietHoursEnd,
                    now(),
                    now()
                )
                ON CONFLICT (profile_id) DO UPDATE SET
                    in_app_enabled = EXCLUDED.in_app_enabled,
                    email_enabled = EXCLUDED.email_enabled,
                    whatsapp_enabled = EXCLUDED.whatsapp_enabled,
                    mentions_enabled = EXCLUDED.mentions_enabled,
                    comments_enabled = EXCLUDED.comments_enabled,
                    reactions_enabled = EXCLUDED.reactions_enabled,
                    connections_enabled = EXCLUDED.connections_enabled,
                    recommendations_enabled = EXCLUDED.recommendations_enabled,
                    digest_frequency = EXCLUDED.digest_frequency,
                    quiet_hours_start = EXCLUDED.quiet_hours_start,
                    quiet_hours_end = EXCLUDED.quiet_hours_end,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("inAppEnabled", inAppEnabled)
                .addValue("emailEnabled", emailEnabled)
                .addValue("whatsappEnabled", whatsappEnabled)
                .addValue("mentionsEnabled", mentionsEnabled)
                .addValue("commentsEnabled", commentsEnabled)
                .addValue("reactionsEnabled", reactionsEnabled)
                .addValue("connectionsEnabled", connectionsEnabled)
                .addValue("recommendationsEnabled", recommendationsEnabled)
                .addValue("digestFrequency", digestFrequency)
                .addValue("quietHoursStart", quietHoursStart == null ? null : Time.valueOf(quietHoursStart))
                .addValue("quietHoursEnd", quietHoursEnd == null ? null : Time.valueOf(quietHoursEnd)));

        outboxService.recordEvent(
                "COMMUNITY_NOTIFICATION_PREFERENCES_UPDATED",
                "PROFILE",
                viewerId,
                viewerId,
                viewerId,
                Map.of("profileId", viewerId)
        );

        return new CommunityNotificationPreferencesDto(
                viewerId,
                inAppEnabled,
                emailEnabled,
                whatsappEnabled,
                mentionsEnabled,
                commentsEnabled,
                reactionsEnabled,
                connectionsEnabled,
                recommendationsEnabled,
                digestFrequency,
                quietHoursStart == null ? null : quietHoursStart.toString(),
                quietHoursEnd == null ? null : quietHoursEnd.toString(),
                OffsetDateTime.now()
        );
    }

    @Transactional
    public CommunityProfileViewTrackResponse trackProfileView(
            UUID viewerId,
            UUID profileId,
            CommunityProfileViewTrackRequest request
    ) {
        requireViewer(viewerId);
        requireId(profileId, "profileId is required");

        if (viewerId.equals(profileId)) {
            return new CommunityProfileViewTrackResponse(profileId, viewerId, false);
        }

        ensureProfileExists(profileId);
        ensureProfilesCanInteract(viewerId, profileId);

        if (!profileViewTrackingEnabled(viewerId) || !profileViewTrackingAllowed(profileId)) {
            return new CommunityProfileViewTrackResponse(profileId, viewerId, false);
        }

        String source = normalizeAnalyticsValue(request == null ? null : request.source(), "direct");
        String discoveryMethod = normalizeOptionalAnalyticsValue(request == null ? null : request.discoveryMethod());
        boolean sessionRelated = request != null && Boolean.TRUE.equals(request.sessionRelated());

        int inserted = jdbc.update("""
                INSERT INTO profile_views (
                    id,
                    viewer_id,
                    viewed_profile_id,
                    viewed_date,
                    source,
                    discovery_method,
                    session_related,
                    viewed_at,
                    created_at
                )
                VALUES (
                    :id,
                    :viewerId,
                    :profileId,
                    CURRENT_DATE,
                    :source,
                    :discoveryMethod,
                    :sessionRelated,
                    now(),
                    now()
                )
                ON CONFLICT (viewer_id, viewed_profile_id, viewed_date) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("viewerId", viewerId)
                .addValue("profileId", profileId)
                .addValue("source", source)
                .addValue("discoveryMethod", discoveryMethod)
                .addValue("sessionRelated", sessionRelated));

        return new CommunityProfileViewTrackResponse(profileId, viewerId, inserted > 0);
    }

    public String normalizeReactionType(String reactionType) {
        String normalized = normalizeToken(reactionType, DEFAULT_REACTION_TYPE);
        if (!DEFAULT_REACTION_TYPE.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported reactionType: " + reactionType);
        }
        return normalized;
    }

    private void requireProfileRelationship(UUID viewerId, UUID targetProfileId) {
        requireViewer(viewerId);
        requireId(targetProfileId, "targetProfileId is required");
        if (viewerId.equals(targetProfileId)) {
            throw new IllegalArgumentException("You cannot connect with yourself");
        }
        ensureProfileExists(targetProfileId);
        ensureProfilesCanInteract(viewerId, targetProfileId);
    }

    private void ensureProfileExists(UUID targetProfileId) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM profiles
                    WHERE id = :targetProfileId
                )
                """, new MapSqlParameterSource("targetProfileId", targetProfileId), Boolean.class));
        if (!exists) {
            throw new NoSuchElementException("Community profile not found");
        }
    }

    private void ensureProfilesCanInteract(UUID viewerId, UUID targetProfileId) {
        boolean blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_blocks b
                    WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = :targetProfileId)
                       OR (b.blocker_profile_id = :targetProfileId AND b.blocked_profile_id = :viewerId)
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("targetProfileId", targetProfileId), Boolean.class));
        if (blocked) {
            throw new SecurityException("Community profiles cannot interact");
        }
    }

    private ConnectionRow findConnectionBetween(UUID viewerId, UUID targetProfileId) {
        List<ConnectionRow> rows = jdbc.query("""
                SELECT id, mentor_id, mentee_id, requester_id, status
                FROM syncs
                WHERE (mentor_id = :viewerId AND mentee_id = :targetProfileId)
                   OR (mentor_id = :targetProfileId AND mentee_id = :viewerId)
                ORDER BY created_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("targetProfileId", targetProfileId), connectionRowMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ConnectionRow requireConnectionForParticipant(UUID viewerId, UUID relationshipId) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT id, mentor_id, mentee_id, requester_id, status
                    FROM syncs
                    WHERE id = :relationshipId
                      AND (mentor_id = :viewerId OR mentee_id = :viewerId)
                    """, new MapSqlParameterSource()
                    .addValue("viewerId", viewerId)
                    .addValue("relationshipId", relationshipId));
            return connectionRowFromMap(row);
        } catch (EmptyResultDataAccessException e) {
            throw new NoSuchElementException("Community connection request not found");
        }
    }

    private RowMapper<ConnectionRow> connectionRowMapper() {
        return (rs, rowNum) -> new ConnectionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("mentor_id", UUID.class),
                rs.getObject("mentee_id", UUID.class),
                rs.getObject("requester_id", UUID.class),
                rs.getString("status")
        );
    }

    private ConnectionRow connectionRowFromMap(Map<String, Object> row) {
        return new ConnectionRow(
                asUuid(row.get("id")),
                asUuid(row.get("mentor_id")),
                asUuid(row.get("mentee_id")),
                asUuid(row.get("requester_id")),
                Objects.toString(row.get("status"), "")
        );
    }

    private UUID asUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        return null;
    }

    private UUID canonicalFirst(UUID firstProfileId, UUID secondProfileId) {
        return firstProfileId.toString().compareTo(secondProfileId.toString()) <= 0
                ? firstProfileId
                : secondProfileId;
    }

    private UUID otherParticipantId(UUID viewerId, ConnectionRow connection) {
        if (viewerId.equals(connection.mentorId())) {
            return connection.menteeId();
        }
        return connection.mentorId();
    }

    private CommunityConnectionResponse toConnectionResponse(UUID viewerId, ConnectionRow connection) {
        UUID targetProfileId = otherParticipantId(viewerId, connection);
        String relationshipStatus = switch (Objects.toString(connection.status(), "").toLowerCase(Locale.ROOT)) {
            case "accepted" -> "connected";
            case "pending" -> viewerId.equals(connection.requesterId()) ? "pending_sent" : "pending_received";
            case "rejected" -> "rejected";
            default -> Objects.toString(connection.status(), "none");
        };
        return new CommunityConnectionResponse(connection.id(), targetProfileId, relationshipStatus);
    }

    private String normalizeConnectionDecision(String status) {
        String normalized = normalizeToken(status, null).toLowerCase(Locale.ROOT);
        if (!List.of("accepted", "rejected").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported connection status: " + status);
        }
        return normalized;
    }

    private String normalizeVisibility(String visibility) {
        String normalized = normalizeToken(visibility, DEFAULT_VISIBILITY);
        if (!List.of("PUBLIC", "CONNECTIONS", "PRIVATE").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported visibility: " + visibility);
        }
        return normalized;
    }

    private String normalizeTargetType(String targetType) {
        String normalized = normalizeToken(targetType, null);
        if (!List.of("POST", "COMMENT", "PROFILE").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported targetType: " + targetType);
        }
        return normalized;
    }

    private String normalizeDigestFrequency(String digestFrequency) {
        String normalized = normalizeToken(digestFrequency, DEFAULT_DIGEST_FREQUENCY);
        if (!List.of("IMMEDIATE", "DAILY", "WEEKLY", "NEVER").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported digestFrequency: " + digestFrequency);
        }
        return normalized;
    }

    private String normalizeToken(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("Value is required");
            }
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureCategoryIsActive(UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_categories
                    WHERE id = :categoryId
                      AND is_active = true
                )
                """, new MapSqlParameterSource("categoryId", categoryId), Boolean.class));
        if (!active) {
            throw new IllegalArgumentException("Community category not found");
        }
    }

    private void ensurePostVisibleForViewer(UUID viewerId, UUID postId) {
        resolveVisiblePostStorage(viewerId, postId);
    }

    private PostStorage resolveVisiblePostStorage(UUID viewerId, UUID postId) {
        if (isCommunityPostVisibleForViewer(viewerId, postId)) {
            return PostStorage.COMMUNITY;
        }
        if (isLegacyPostVisibleForViewer(viewerId, postId)) {
            return PostStorage.LEGACY;
        }
        throw new NoSuchElementException("Community post not found");
    }

    private PostStorage resolveOwnedPostStorage(UUID viewerId, UUID postId) {
        if (isCommunityPostOwner(viewerId, postId)) {
            return PostStorage.COMMUNITY;
        }
        if (isLegacyPostOwner(viewerId, postId)) {
            return PostStorage.LEGACY;
        }
        throw new SecurityException("Only the post author can modify this post");
    }

    private PostStorage resolveExistingPostStorage(UUID postId) {
        if (isCommunityPostKnown(postId)) {
            return PostStorage.COMMUNITY;
        }
        if (isLegacyPostKnown(postId)) {
            return PostStorage.LEGACY;
        }
        return PostStorage.LEGACY;
    }

    private boolean isCommunityPostVisibleForViewer(UUID viewerId, UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_posts p
                    WHERE p.id = :postId
                      AND p.status = 'ACTIVE'
                      AND p.moderation_status = 'APPROVED'
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.author_profile_id)
                           OR (b.blocker_profile_id = p.author_profile_id AND b.blocked_profile_id = :viewerId)
                      )
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), Boolean.class));
    }

    private boolean isLegacyPostVisibleForViewer(UUID viewerId, UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM posts p
                    WHERE p.id = :postId
                      AND (COALESCE(p.is_hidden, false) = false OR p.user_id = :viewerId)
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = p.user_id)
                           OR (b.blocker_profile_id = p.user_id AND b.blocked_profile_id = :viewerId)
                      )
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), Boolean.class));
    }

    private void ensurePostOwner(UUID viewerId, UUID postId) {
        resolveOwnedPostStorage(viewerId, postId);
    }

    private void ensureLegacyPostOwner(UUID viewerId, UUID postId) {
        if (!isLegacyPostOwner(viewerId, postId)) {
            throw new SecurityException("Only the post author can modify this post");
        }
    }

    private boolean isCommunityPostOwner(UUID viewerId, UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_posts
                    WHERE id = :postId
                      AND author_profile_id = :viewerId
                      AND status <> 'DELETED'
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), Boolean.class));
    }

    private boolean isLegacyPostOwner(UUID viewerId, UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM posts
                    WHERE id = :postId
                      AND user_id = :viewerId
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("postId", postId), Boolean.class));
    }

    private boolean isCommunityPostKnown(UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_posts
                    WHERE id = :postId
                      AND status <> 'DELETED'
                )
                """, new MapSqlParameterSource("postId", postId), Boolean.class));
    }

    private boolean isLegacyPostKnown(UUID postId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM posts
                    WHERE id = :postId
                )
                """, new MapSqlParameterSource("postId", postId), Boolean.class));
    }

    private void ensureReplyTarget(UUID postId, UUID parentCommentId) {
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM community_comments
                    WHERE id = :parentCommentId
                      AND post_id = :postId
                      AND parent_comment_id IS NULL
                      AND status = 'ACTIVE'
                )
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("parentCommentId", parentCommentId), Boolean.class));
        if (!valid) {
            throw new IllegalArgumentException("Parent comment not found");
        }
    }

    private void ensureLegacyReplyTarget(UUID postId, UUID parentCommentId) {
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM post_comments
                    WHERE id = :parentCommentId
                      AND post_id = :postId
                      AND parent_id IS NULL
                )
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("parentCommentId", parentCommentId), Boolean.class));
        if (!valid) {
            throw new IllegalArgumentException("Parent comment not found");
        }
    }

    private void ensureLegacyCommentVisibleForViewer(UUID viewerId, UUID commentId) {
        boolean visible = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM post_comments c
                    JOIN posts p ON p.id = c.post_id
                    WHERE c.id = :commentId
                      AND (COALESCE(p.is_hidden, false) = false OR p.user_id = :viewerId)
                      AND NOT EXISTS (
                        SELECT 1
                        FROM community_blocks b
                        WHERE (b.blocker_profile_id = :viewerId AND b.blocked_profile_id = c.user_id)
                           OR (b.blocker_profile_id = c.user_id AND b.blocked_profile_id = :viewerId)
                      )
                )
                """, new MapSqlParameterSource()
                .addValue("viewerId", viewerId)
                .addValue("commentId", commentId), Boolean.class));
        if (!visible) {
            throw new NoSuchElementException("Community comment not found");
        }
    }

    private UUID requireCommentOwnerAndPost(UUID viewerId, UUID commentId) {
        try {
            return jdbc.queryForObject("""
                    SELECT post_id
                    FROM community_comments
                    WHERE id = :commentId
                      AND author_profile_id = :viewerId
                      AND status <> 'DELETED'
                    """, new MapSqlParameterSource()
                    .addValue("viewerId", viewerId)
                    .addValue("commentId", commentId), UUID.class);
        } catch (EmptyResultDataAccessException e) {
            throw new SecurityException("Only the comment author can delete this comment");
        }
    }

    private UUID legacyCommentOwnerPostId(UUID viewerId, UUID commentId) {
        try {
            return jdbc.queryForObject("""
                    SELECT post_id
                    FROM post_comments
                    WHERE id = :commentId
                      AND user_id = :viewerId
                    """, new MapSqlParameterSource()
                    .addValue("viewerId", viewerId)
                    .addValue("commentId", commentId), UUID.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void ensureReportTargetExists(String targetType, UUID targetId) {
        String sql = switch (targetType) {
            case "POST" -> """
                    SELECT EXISTS (
                        SELECT 1
                        FROM community_posts
                        WHERE id = :targetId
                          AND status <> 'DELETED'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM posts
                        WHERE id = :targetId
                    )
                    """;
            case "COMMENT" -> """
                    SELECT EXISTS (
                        SELECT 1
                        FROM community_comments
                        WHERE id = :targetId
                          AND status <> 'DELETED'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM post_comments
                        WHERE id = :targetId
                    )
                    """;
            case "PROFILE" -> """
                    SELECT EXISTS (
                        SELECT 1
                        FROM profiles
                        WHERE id = :targetId
                    )
                    """;
            default -> throw new IllegalArgumentException("Unsupported targetType: " + targetType);
        };
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("targetId", targetId),
                Boolean.class
        ));
        if (!exists) {
            throw new NoSuchElementException("Report target not found");
        }
    }

    private CommunityPostMutationResponse loadPost(UUID postId) {
        List<CommunityPostMutationResponse> rows = jdbc.query("""
                SELECT
                    id,
                    author_profile_id,
                    category_id,
                    content,
                    visibility,
                    status,
                    moderation_status,
                    media_url,
                    media_type,
                    image_url,
                    link_url,
                    link_title,
                    link_description,
                    link_image,
                    hashtags,
                    likes_count,
                    comments_count,
                    saves_count,
                    reports_count,
                    created_at,
                    updated_at
                FROM community_posts
                WHERE id = :postId
                """, new MapSqlParameterSource("postId", postId), postMutationMapper());
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Community post not found");
        }
        return rows.get(0);
    }

    private int countPostReactions(UUID postId, String reactionType) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM community_post_reactions
                WHERE post_id = :postId
                  AND reaction_type = :reactionType
                """, new MapSqlParameterSource()
                .addValue("postId", postId)
                .addValue("reactionType", reactionType), Integer.class);
        return count == null ? 0 : count;
    }

    private int countLegacyPostLikes(UUID postId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM post_likes
                WHERE post_id = :postId
                """, new MapSqlParameterSource("postId", postId), Integer.class);
        return count == null ? 0 : count;
    }

    private int countLegacyPostSaves(UUID postId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM saved_posts
                WHERE post_id = :postId
                """, new MapSqlParameterSource("postId", postId), Integer.class);
        return count == null ? 0 : count;
    }

    private int countLegacyCommentLikes(UUID commentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM comment_likes
                WHERE comment_id = :commentId
                """, new MapSqlParameterSource("commentId", commentId), Integer.class);
        return count == null ? 0 : count;
    }

    private int getPostCounter(UUID postId, String counterColumn) {
        if (!List.of("likes_count", "comments_count", "saves_count", "reports_count").contains(counterColumn)) {
            throw new IllegalArgumentException("Unsupported post counter: " + counterColumn);
        }
        Integer count = jdbc.queryForObject(
                "SELECT " + counterColumn + " FROM community_posts WHERE id = :postId",
                new MapSqlParameterSource("postId", postId),
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private CommunityNotificationPreferencesDto defaultNotificationPreferences(UUID profileId) {
        return new CommunityNotificationPreferencesDto(
                profileId,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                DEFAULT_DIGEST_FREQUENCY,
                null,
                null,
                OffsetDateTime.now()
        );
    }

    private boolean defaultTrue(Boolean value) {
        return value == null || value;
    }

    private LocalTime parseQuietHour(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(fieldName + " must use HH:mm format");
        }
    }

    private String requireText(String value, String message) {
        String trimmed = trimmed(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private boolean profileViewTrackingEnabled(UUID viewerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COALESCE((
                    SELECT track_profile_views
                    FROM analytics_settings
                    WHERE user_id = :viewerId
                    LIMIT 1
                ), true)
                """, new MapSqlParameterSource("viewerId", viewerId), Boolean.class));
    }

    private boolean profileViewTrackingAllowed(UUID profileId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT COALESCE((
                    SELECT allow_profile_view_tracking
                    FROM analytics_settings
                    WHERE user_id = :profileId
                    LIMIT 1
                ), true)
                """, new MapSqlParameterSource("profileId", profileId), Boolean.class));
    }

    private String normalizeAnalyticsValue(String value, String fallback) {
        String normalized = trimmed(value);
        if (normalized == null) {
            normalized = fallback;
        }
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    private String normalizeOptionalAnalyticsValue(String value) {
        String normalized = trimmed(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    private UUID requireId(UUID value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private void requireViewer(UUID viewerId) {
        if (viewerId == null) {
            throw new IllegalArgumentException("Authenticated profile is required");
        }
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeHashtags(List<String> hashtags) {
        if (hashtags == null) {
            return List.of();
        }
        return hashtags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(20)
                .toList();
    }

    private RowMapper<CommunityCategoryItem> categoryMapper() {
        return (rs, rowNum) -> new CommunityCategoryItem(
                uuid(rs, "id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("sort_order")
        );
    }

    private RowMapper<CommunityPostMutationResponse> postMutationMapper() {
        return (rs, rowNum) -> new CommunityPostMutationResponse(
                uuid(rs, "id"),
                uuid(rs, "author_profile_id"),
                nullableUuid(rs, "category_id"),
                rs.getString("content"),
                rs.getString("visibility"),
                rs.getString("status"),
                rs.getString("moderation_status"),
                rs.getString("media_url"),
                rs.getString("media_type"),
                rs.getString("image_url"),
                rs.getString("link_url"),
                rs.getString("link_title"),
                rs.getString("link_description"),
                rs.getString("link_image"),
                stringArray(rs, "hashtags"),
                rs.getInt("likes_count"),
                rs.getInt("comments_count"),
                rs.getInt("saves_count"),
                rs.getInt("reports_count"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at")
        );
    }

    private RowMapper<CommunityCommentItem> commentMapper() {
        return (rs, rowNum) -> new CommunityCommentItem(
                uuid(rs, "id"),
                uuid(rs, "post_id"),
                uuid(rs, "author_profile_id"),
                nullableUuid(rs, "parent_comment_id"),
                rs.getString("content"),
                rs.getString("status"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at"),
                new CommunityProfileSummary(
                        uuid(rs, "profile_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("avatar_url"),
                        rs.getString("role"),
                        rs.getString("headline"),
                        rs.getString("industry"),
                        rs.getString("country"),
                        rs.getBoolean("is_verified")
                )
        );
    }

    private RowMapper<CommunityReportResponse> reportMapper() {
        return (rs, rowNum) -> new CommunityReportResponse(
                uuid(rs, "id"),
                rs.getString("target_type"),
                uuid(rs, "target_id"),
                rs.getString("status")
        );
    }

    private RowMapper<CommunityNotificationPreferencesDto> notificationPreferencesMapper() {
        return (rs, rowNum) -> new CommunityNotificationPreferencesDto(
                uuid(rs, "profile_id"),
                rs.getBoolean("in_app_enabled"),
                rs.getBoolean("email_enabled"),
                rs.getBoolean("whatsapp_enabled"),
                rs.getBoolean("mentions_enabled"),
                rs.getBoolean("comments_enabled"),
                rs.getBoolean("reactions_enabled"),
                rs.getBoolean("connections_enabled"),
                rs.getBoolean("recommendations_enabled"),
                rs.getString("digest_frequency"),
                timeString(rs, "quiet_hours_start"),
                timeString(rs, "quiet_hours_end"),
                offsetDateTime(rs, "updated_at")
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        return rs.getObject(column, OffsetDateTime.class);
    }

    private List<String> stringArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        if (value instanceof Object[] objects) {
            return Arrays.stream(objects)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    private String timeString(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalTime localTime) {
            return localTime.toString();
        }
        return value.toString();
    }
}
