package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CohortRosterParticipantRequest {

    private UUID profileId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String chapter;
    private String region;

    @Builder.Default
    private List<String> interestTags = List.of();
}
