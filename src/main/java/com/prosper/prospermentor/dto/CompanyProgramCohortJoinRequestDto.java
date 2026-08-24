package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohortJoinRequest;
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
public class CompanyProgramCohortJoinRequestDto {
    private UUID id;
    private UUID cohortId;
    private UUID companyProgramId;
    private String submittedEmail;
    private String submittedPhone;
    private String submittedFirstName;
    private String submittedLastName;
    private String submittedChapter;
    private String submittedRegion;
    private List<String> interestTags;
    private UUID matchedProfileId;
    private String matchedProfileName;
    private CompanyProgramCohortJoinRequest.JoinRequestStatus status;
    private UUID reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
