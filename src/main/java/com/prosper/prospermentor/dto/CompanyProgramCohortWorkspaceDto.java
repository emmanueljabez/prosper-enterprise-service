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
public class CompanyProgramCohortWorkspaceDto {
    private UUID cohortId;
    private long enrolledCount;
    private long selfJoinedCount;
    private long pendingConfirmationCount;
    private long duplicateReviewCount;
    private long plenaryAttendedCount;
    private double plenaryAttendanceRate;
    private long circleCount;
    private long unplacedCount;
    private long matchedCount;
    private double matchCompletionRate;
    private long additionalSessionRequestCount;
    private double feedbackResponseRate;
    private List<String> riskIndicators;
}
