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
public class CircleSuggestionDto {
    private String name;
    private String theme;
    private List<String> interestTags;
    private List<UUID> cohortParticipantIds;
    private List<String> participantNames;
    private int participantCount;
}
