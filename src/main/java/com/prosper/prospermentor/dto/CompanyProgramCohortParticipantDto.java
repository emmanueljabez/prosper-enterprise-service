package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProgramCohortParticipantDto {
    private UUID id;
    private UUID cohortId;
    private UUID companyProgramId;
    private UUID companyProgramParticipantId;
    private UUID profileId;
    private String profileName;
    private String profileEmail;
    private String profilePhone;
    private CompanyProgramCohortParticipant.ParticipantSource source;
    private CompanyProgramCohortParticipant.CohortParticipantStatus status;
    private String chapter;
    private String region;
    private List<String> interestTags;
    private CompanyProgramCohortParticipant.DuplicateStatus duplicateStatus;
    private UUID duplicateCandidateProfileId;
    private UUID confirmedByUserId;
    private LocalDateTime confirmedAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
