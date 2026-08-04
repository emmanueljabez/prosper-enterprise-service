package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewStatusDto {
    private UUID reviewRequestId;
    private UUID reviewCycleId;
    private UUID companyProgramId;
    private String companyProgramName;
    private UUID participantId;
    private UUID sessionId;
    private String sessionTitle;
    private ZonedDateTime sessionScheduledStart;
    private ReviewCycle.ReviewType reviewType;
    private ReviewRequest.ReviewRole reviewerRole;
    private String targetName;
    private ReviewRequest.ReviewRequestStatus requestStatus;
    private ReviewCycle.ReviewCycleStatus cycleStatus;
    private LocalDateTime sentAt;
    private LocalDateTime submittedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revealedAt;
    private Integer answeredQuestions;
    private Double overallScore;
    private Boolean recommendContinue;
    private String optionalComment;
    private boolean actionRequired;
    private boolean awaitingReveal;
    private boolean revealed;
    private boolean expired;
    private boolean deliveryIssue;
}
