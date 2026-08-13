package com.prosper.prospermentor.dto.community;

import java.time.OffsetDateTime;
import java.util.List;
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
            CommunityProfileSummary author
    ) {
    }

    public record CommunityFeedResponse(
            List<CommunityPostItem> posts,
            String mode,
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
}
