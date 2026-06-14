package com.prosper.prospermentor.dto;

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
public class CompanyParticipantPulseSummaryDto {
    private UUID companyId;
    private long totalPulses;
    private long pendingPulses;
    private long completedPulses;
    private long expiredPulses;
    private Double completionRate;
    private Double averageConfidenceScore;
    private Double averageSatisfactionScore;
    private Double averageGoalClarityScore;
    private List<CompanyProgramPulseSummaryDto> programs;
}
