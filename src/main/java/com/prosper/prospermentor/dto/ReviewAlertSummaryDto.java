package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAlertSummaryDto {

    private UUID companyId;
    private UUID companyProgramId;
    private long totalReviewCycles;
    private long revealedReviewCycles;
    private long pendingReviewCycles;
    private long totalAlerts;
    private long openAlerts;
    private long acknowledgedAlerts;
    private long resolvedAlerts;
    private long highSeverityAlerts;
    private long lowMentorScoreAlerts;
    private long lowMenteeScoreAlerts;
    private long lowFitAlerts;
    private long doNotContinueAlerts;
    private long rematchRecommendedAlerts;
    private List<ReviewAlertAdminDto> recentAlerts;
}
