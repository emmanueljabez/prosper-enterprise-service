package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.CompanyProgram;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class CompanyReportDtos {

    private CompanyReportDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportListDto<T> {
        private List<T> rows;
        private int count;
        private int currentPage;
        private int pageSize;
        private int totalPages;
        private long totalItems;
        private boolean hasNext;
        private boolean hasPrevious;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramReportRowDto {
        private UUID id;
        private String name;
        private CompanyProgram.CompanyProgramStatus status;
        private CompanyProgram.MatchingMode matchingMode;
        private String objective;
        private String targetAudienceDescription;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer maxParticipants;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantReportRowDto {
        private UUID id;
        private UUID companyProgramId;
        private String companyProgramName;
        private UUID profileId;
        private String profileName;
        private String profileEmail;
        private String profileRole;
        private String department;
        private String status;
        private UUID mentorId;
        private String mentorName;
        private String mentorEmail;
        private String matchStatus;
        private LocalDateTime enrolledAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorMatchReportRowDto {
        private UUID participantId;
        private UUID companyProgramId;
        private String companyProgramName;
        private String participantName;
        private String participantEmail;
        private String participantStatus;
        private String matchingMode;
        private String matchStatus;
        private UUID mentorId;
        private String mentorName;
        private String mentorEmail;
        private Integer shortlistCount;
        private LocalDateTime selectionDeadlineAt;
        private LocalDateTime assignedAt;
        private LocalDateTime resolvedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionReportRowDto {
        private UUID id;
        private String employeeName;
        private String employeeEmail;
        private String department;
        private String mentorName;
        private String title;
        private String status;
        private String platformDisplayName;
        private ZonedDateTime scheduledStart;
        private ZonedDateTime scheduledEnd;
        private Long durationMin;
        private Integer rating;
        private String reviewStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PulseCoverageReportRowDto {
        private UUID companyProgramId;
        private String companyProgramName;
        private int totalPulses;
        private int completedPulses;
        private int pendingPulses;
        private int expiredPulses;
        private int baselinePulses;
        private int programEndPulses;
        private BigDecimal completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskSignalReportRowDto {
        private UUID id;
        private String alertType;
        private String severity;
        private String status;
        private UUID companyProgramId;
        private String companyProgramName;
        private UUID participantId;
        private String participantName;
        private String participantEmail;
        private UUID mentorId;
        private String mentorName;
        private String questionCode;
        private BigDecimal scoreValue;
        private String details;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingTransactionReportRowDto {
        private UUID id;
        private String paymentType;
        private String paymentMethod;
        private String status;
        private BigDecimal amount;
        private String currency;
        private UUID invoiceId;
        private UUID sessionId;
        private String mpesaReceiptNumber;
        private String gatewayReference;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }
}
