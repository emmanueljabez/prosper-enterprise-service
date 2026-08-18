package com.prosper.prospermentor.dto.community;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommunityDtos {
    private CommunityDtos() {
    }

    public record CommunityProfileSummary(
            UUID id,
            String firstName,
            String lastName,
            String avatarUrl,
            String role,
            String headline,
            String industry,
            String country,
            Boolean verified
    ) {
    }

    public record CommunityPostItem(
            UUID id,
            UUID userId,
            String content,
            String mediaUrl,
            String mediaType,
            String imageUrl,
            String linkUrl,
            String linkTitle,
            String linkDescription,
            String linkImage,
            int likesCount,
            int commentsCount,
            OffsetDateTime createdAt,
            CommunityProfileSummary author,
            Boolean hidden,
            Boolean reactedByViewer,
            Boolean savedByViewer,
            String linkDomain,
            String linkSiteName,
            Integer impressionsCount
    ) {
    }

    public record CommunityFeedResponse(
            List<CommunityPostItem> posts,
            String mode,
            int limit
    ) {
    }

    public record CommunitySavedPostsResponse(
            List<CommunityPostItem> posts,
            int limit
    ) {
    }

    public record CommunityMyPostsResponse(
            List<CommunityPostItem> posts,
            int limit
    ) {
    }

    public record CommunityCategoryItem(
            UUID id,
            String slug,
            String name,
            String description,
            int sortOrder
    ) {
    }

    public record CommunityHashtagItem(
            String tag,
            int postsCount
    ) {
    }

    public record CommunitySearchPersonItem(
            CommunityProfileSummary profile,
            String relationshipStatus,
            int score,
            List<RecommendationReason> reasons
    ) {
    }

    public record CommunitySearchResponse(
            String query,
            String type,
            int limit,
            List<CommunityPostItem> posts,
            List<CommunitySearchPersonItem> people,
            List<CommunityCategoryItem> categories,
            List<CommunityHashtagItem> hashtags
    ) {
    }

    public record CommunityPostRequest(
            UUID categoryId,
            String content,
            String visibility,
            String mediaUrl,
            String mediaType,
            String imageUrl,
            String linkUrl,
            String linkTitle,
            String linkDescription,
            String linkImage,
            List<String> hashtags
    ) {
    }

    public record CommunityPostMutationResponse(
            UUID id,
            UUID userId,
            UUID categoryId,
            String content,
            String visibility,
            String status,
            String moderationStatus,
            String mediaUrl,
            String mediaType,
            String imageUrl,
            String linkUrl,
            String linkTitle,
            String linkDescription,
            String linkImage,
            List<String> hashtags,
            int likesCount,
            int commentsCount,
            int savesCount,
            int reportsCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CommunityReactionRequest(
            String reactionType
    ) {
    }

    public record CommunityReactionResponse(
            UUID postId,
            String reactionType,
            boolean reacted,
            int likesCount
    ) {
    }

    public record CommunityPostHiddenRequest(
            Boolean hidden
    ) {
    }

    public record CommunityPostHiddenResponse(
            UUID postId,
            boolean hidden
    ) {
    }

    public record CommunityCommentRequest(
            UUID parentCommentId,
            String content
    ) {
    }

    public record CommunityCommentItem(
            UUID id,
            UUID postId,
            UUID authorProfileId,
            UUID parentCommentId,
            String content,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            CommunityProfileSummary author
    ) {
    }

    public record CommunityCommentReactionResponse(
            UUID commentId,
            String reactionType,
            boolean reacted,
            int likesCount
    ) {
    }

    public record CommunitySavedPostResponse(
            UUID postId,
            boolean saved,
            int savesCount
    ) {
    }

    public record CommunityBlockRequest(
            UUID blockedProfileId,
            String reason
    ) {
    }

    public record CommunityBlockResponse(
            UUID blockedProfileId,
            boolean blocked
    ) {
    }

    public record CommunityReportRequest(
            String targetType,
            UUID targetId,
            String reasonCode,
            String reasonDetail
    ) {
    }

    public record CommunityReportResponse(
            UUID id,
            String targetType,
            UUID targetId,
            String status
    ) {
    }

    public record CommunityNotificationPreferencesRequest(
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean whatsappEnabled,
            Boolean mentionsEnabled,
            Boolean commentsEnabled,
            Boolean reactionsEnabled,
            Boolean connectionsEnabled,
            Boolean recommendationsEnabled,
            String digestFrequency,
            String quietHoursStart,
            String quietHoursEnd
    ) {
    }

    public record CommunityNotificationPreferencesDto(
            UUID profileId,
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean whatsappEnabled,
            boolean mentionsEnabled,
            boolean commentsEnabled,
            boolean reactionsEnabled,
            boolean connectionsEnabled,
            boolean recommendationsEnabled,
            String digestFrequency,
            String quietHoursStart,
            String quietHoursEnd,
            OffsetDateTime updatedAt
    ) {
    }

    public record RecommendationReason(
            String code,
            String label
    ) {
    }

    public record RecommendedPerson(
            CommunityProfileSummary profile,
            int score,
            List<RecommendationReason> reasons
    ) {
    }

    public record RecommendedPeopleResponse(
            List<RecommendedPerson> people,
            int limit
    ) {
    }

    public record NetworkMember(
            UUID relationshipId,
            CommunityProfileSummary profile,
            String relationshipStatus,
            OffsetDateTime connectedAt
    ) {
    }

    public record NetworkOverviewResponse(
            List<NetworkMember> connections,
            List<NetworkMember> followers,
            List<NetworkMember> following,
            List<NetworkMember> pendingRequests,
            List<NetworkMember> sentRequests
    ) {
    }

    public record CommunityProfileNetworkResponse(
            UUID profileId,
            List<NetworkMember> connections,
            List<NetworkMember> followers,
            List<NetworkMember> following,
            List<NetworkMember> reciprocalFollows,
            int totalCount
    ) {
    }

    public record CommunityFollowResponse(
            UUID targetProfileId,
            boolean following
    ) {
    }

    public record CommunityConnectionStatusRequest(
            String status
    ) {
    }

    public record CommunityConnectionResponse(
            UUID relationshipId,
            UUID targetProfileId,
            String relationshipStatus
    ) {
    }

    public record CommunityPeopleDiscoveryResponse(
            List<RecommendedPerson> suggestedPeople,
            List<NetworkMember> recentConnections,
            int limit
    ) {
    }

    public record CommunityRealtimeEventItem(
            UUID id,
            String type,
            String sourceType,
            String aggregateType,
            UUID aggregateId,
            UUID actorProfileId,
            UUID recipientProfileId,
            Map<String, Object> payload,
            OffsetDateTime createdAt
    ) {
    }

    public record CommunityRealtimeEventsResponse(
            List<CommunityRealtimeEventItem> events,
            int limit
    ) {
    }
}
