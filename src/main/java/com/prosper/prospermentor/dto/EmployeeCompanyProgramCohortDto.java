package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramCohort;
import com.prosper.prospermentor.entity.CompanyProgramCohortParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCompanyProgramCohortDto {
    private UUID cohortId;
    private UUID companyProgramId;
    private UUID cohortParticipantId;
    private UUID companyProgramParticipantId;
    private String cohortName;
    private String companyProgramName;
    private String companyName;
    private String chapter;
    private String region;
    private CompanyProgramCohort.CohortStatus cohortStatus;
    private CompanyProgramCohortParticipant.CohortParticipantStatus participantStatus;
    private StageSummaryDto stages;
    private CommonInterestCircleDto circle;
    private MentorAssignmentSummaryDto mentorAssignment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageSummaryDto {
        private StageDto plenary;
        private StageDto circle;
        private StageDto oneToOne;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDto {
        private String status;
        private String blockedReason;
    }
}
