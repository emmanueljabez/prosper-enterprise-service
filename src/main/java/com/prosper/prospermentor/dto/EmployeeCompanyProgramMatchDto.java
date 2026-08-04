package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
import com.prosper.prospermentor.entity.CompanyProgramParticipant;
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
public class EmployeeCompanyProgramMatchDto {
    private UUID participantId;
    private UUID companyProgramId;
    private UUID companyId;
    private String companyName;
    private String programName;
    private String templateProgramName;
    private String catalogJourneySummary;
    private List<CompanyProgramCatalogStageDto> catalogStages;
    private CompanyProgram.CompanyProgramStatus programStatus;
    private CompanyProgram.MatchingMode matchingMode;
    private CompanyProgramParticipant.ParticipantStatus participantStatus;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private MentorAssignmentSummaryDto mentorAssignment;
    private MatchWorkspaceSummaryDto matchWorkspace;
}
