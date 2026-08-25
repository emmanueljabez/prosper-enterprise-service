package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohort;
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
public class CohortSelfJoinResponseDto {
    private UUID joinRequestId;
    private UUID cohortId;
    private UUID companyProgramId;
    private UUID companyId;
    private String companyProgramName;
    private String companyName;
    private String cohortName;
    private String chapter;
    private String region;
    private CompanyProgramCohort.CohortStatus cohortStatus;
    private CompanyProgramCohortJoinRequest.JoinRequestStatus status;
    private boolean duplicateReviewRequired;
    private UUID matchedProfileId;
    private List<String> interestTagSet;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
}
