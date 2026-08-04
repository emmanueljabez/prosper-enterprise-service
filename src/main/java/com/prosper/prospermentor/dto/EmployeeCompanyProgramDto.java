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
public class EmployeeCompanyProgramDto {
    private UUID participantId;
    private CompanyProgramParticipant.ParticipantStatus participantStatus;
    private LocalDateTime enrolledAt;
    private UUID companyProgramId;
    private UUID companyId;
    private String companyName;
    private UUID templateProgramId;
    private String templateProgramName;
    private String catalogJourneySummary;
    private List<CompanyProgramCatalogStageDto> catalogStages;
    private UUID journeyTemplateId;
    private String journeyTemplateName;
    private String name;
    private String objective;
    private String targetAudienceDescription;
    private CompanyProgram.CompanyProgramStatus status;
    private CompanyProgram.MatchingMode matchingMode;
    private ParticipantConsentSummaryDto consentSummary;
    private Integer maxParticipants;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
}
