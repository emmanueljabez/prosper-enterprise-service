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
