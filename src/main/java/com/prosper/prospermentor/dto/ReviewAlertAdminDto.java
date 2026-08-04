package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.ReviewAlert;
import com.prosper.prospermentor.entity.ReviewCycle;
import com.prosper.prospermentor.entity.ReviewRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAlertAdminDto {

    private UUID id;
    private UUID reviewCycleId;
    private ReviewCycle.ReviewType reviewType;
    private UUID reviewRequestId;
    private ReviewRequest.ReviewRole reviewerRole;
    private ReviewRequest.ReviewRole targetRole;
    private UUID companyProgramId;
    private String companyProgramName;
    private UUID participantId;
    private String participantName;
    private String participantEmail;
    private String participantStatus;
    private UUID mentorAssignmentId;
    private UUID mentorId;
    private String mentorName;
    private ReviewAlert.ReviewAlertType alertType;
    private ReviewAlert.Severity severity;
    private ReviewAlert.ReviewAlertStatus status;
    private String questionCode;
    private BigDecimal scoreValue;
    private Boolean booleanValue;
    private String details;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
