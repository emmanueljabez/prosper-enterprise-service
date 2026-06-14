package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCompanyProgramRequest {
    private UUID programId;
    private List<CompanyProgramCatalogStageRequest> catalogStages = new ArrayList<>();
    private UUID journeyTemplateId;
    private String name;
    private String objective;
    private String targetAudienceDescription;
    private CompanyProgram.MatchingMode matchingMode;
    @Positive(message = "Employee selection window must be greater than 0")
    private Integer employeeSelectionWindowHours;
    @Positive(message = "Employee selection shortlist size must be greater than 0")
    private Integer employeeSelectionShortlistSize;
    private Boolean requiresMentorForSessionSteps;
    private JourneyTemplateUpdateScope journeyTemplateUpdateScope;
    private String visibilityPolicyCode;

    @Positive(message = "Max participants must be greater than 0")
    private Integer maxParticipants;

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    public enum JourneyTemplateUpdateScope {
        FUTURE_ENROLLMENTS_ONLY,
        MIGRATE_NOT_STARTED_PARTICIPANTS
    }
}
