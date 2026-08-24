package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircleSuggestionResultDto {
    private UUID cohortId;
    private List<CircleSuggestionDto> suggestedCircles;
    private List<UUID> unplacedParticipantIds;
    private List<String> unplacedParticipantNames;
}
