package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgramParticipant;
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
public class ParticipantConsentWorkspaceDto {
    private UUID participantId;
    private UUID companyProgramId;
    private String companyProgramName;
    private CompanyProgramParticipant.ParticipantStatus participantStatus;
    private ParticipantConsentSummaryDto summary;
    private List<ParticipantConsentDecisionDto> consents;
}
