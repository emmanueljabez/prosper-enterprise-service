package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantPulseSummaryDto {
    private long totalPulses;
    private long pendingPulses;
    private long completedPulses;
    private long expiredPulses;
    private long baselinePendingPulses;
    private long programEndPendingPulses;
    private Double completionRate;
    private Double averageConfidenceScore;
    private Double averageSatisfactionScore;
    private Double averageGoalClarityScore;
}
