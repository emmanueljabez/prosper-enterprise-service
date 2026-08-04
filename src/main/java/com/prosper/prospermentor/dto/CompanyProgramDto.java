package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
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
public class CompanyProgramDto {
    private UUID id;
    private UUID companyId;
    private String companyName;
    private UUID templateProgramId;
    private String templateProgramName;
    private String catalogJourneySummary;
    private Integer catalogProgramCount;
    private List<CompanyProgramCatalogStageDto> catalogStages;
    private UUID journeyTemplateId;
    private String journeyTemplateName;
    private String name;
    private String objective;
    private String targetAudienceDescription;
    private CompanyProgram.CompanyProgramStatus status;
    private CompanyProgram.MatchingMode matchingMode;
    private Integer employeeSelectionWindowHours;
    private Integer employeeSelectionShortlistSize;
    private Boolean requiresMentorForSessionSteps;
    private String visibilityPolicyCode;
    private Integer maxParticipants;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private UUID createdByUserId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
