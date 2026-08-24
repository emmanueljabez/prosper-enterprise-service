package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonInterestCircleMemberDto {
    private UUID membershipId;
    private UUID circleId;
    private UUID cohortParticipantId;
    private UUID profileId;
    private String profileName;
    private String profileEmail;
    private CommonInterestCircleMembership.PlacementSource placementSource;
    private CommonInterestCircleMembership.MembershipStatus status;
    private UUID placedByUserId;
    private LocalDateTime placedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
