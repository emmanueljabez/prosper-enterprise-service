package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CommonInterestCircleMembership;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCircleParticipantRequest {
    private UUID cohortParticipantId;
    private CommonInterestCircleMembership.PlacementSource placementSource;
}
