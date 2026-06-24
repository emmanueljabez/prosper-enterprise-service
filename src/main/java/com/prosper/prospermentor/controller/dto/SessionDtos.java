package com.prosper.prospermentor.controller.dto;

import com.prosper.prospermentor.dto.AddOnSessionDto;
import com.prosper.prospermentor.dto.RecommendedPlanDto;
import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.SessionOutcomeActionItem;
import com.prosper.prospermentor.entity.SessionProposal;
import com.prosper.prospermentor.entity.SessionSupportRequest;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for session-related API operations
 * Separate DTOs for request/response to follow API design best practices
 */
public class SessionDtos {
    
    /**
     * DTO for topic/skill information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicDto {
        private UUID id;
        private String name;
        private long mentorCount;
    }
    
    /**
     * DTO for mentor information in booking context
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorDto {
        private UUID id;
        private String title;
        private String company;
        private Integer yearsExperience;
        private BigDecimal hourlyRate;
        private BigDecimal rating;
        private Integer totalSessions;
        private String bio;
        private String avatarUrl;
        private Boolean isAvailable;
    }

    
    /**
     * Request DTO for confirming a session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmSessionRequestDto {
        private String mentorResponse;
        private ZonedDateTime scheduledStart;
    }
    
    /**
     * Request DTO for cancelling a session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelSessionRequestDto {

        @NotNull(message = "Cancelled by is required")
        private Session.CancelledBy cancelledBy;

        private String reason;
    }

    /**
     * Request DTO for declining a session (mentor action)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeclineSessionRequestDto {
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposedSessionSlotRequestDto {
        @NotNull(message = "scheduledStart is required")
        private ZonedDateTime scheduledStart;
        private ZonedDateTime scheduledEnd;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposeSessionAlternativeRequestDto {
        private String mentorMessage;
        private List<ProposedSessionSlotRequestDto> slots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RespondToSessionProposalRequestDto {
        private UUID slotId;
        private String response;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactSessionSupportRequestDto {
        @NotNull(message = "requesterType is required")
        private SessionSupportRequest.RequesterType requesterType;
        private String message;
    }

    /**
     * Request DTO for completing a session with structured outcomes.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteSessionRequestDto {
        private String outcomeSummary;
        private String reflectionPrompt;
        private String mentorPrivateNotes;
        private List<OutcomeActionItemRequestDto> actionItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutcomeActionItemRequestDto {
        private String description;
        private SessionOutcomeActionItem.ActionItemOwnerType ownerType;
        private ZonedDateTime dueAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionActionItemDto {
        private UUID id;
        private String description;
        private SessionOutcomeActionItem.ActionItemOwnerType ownerType;
        private ZonedDateTime dueAt;
        private LocalDateTime completedAt;
        private boolean completed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionOutcomeDto {
        private UUID id;
        private String summary;
        private String reflectionPrompt;
        private LocalDateTime recordedAt;
        private Integer openActionItemCount;
        private List<SessionActionItemDto> actionItems;
    }
    
    /**
     * Response DTO for session information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponseDto {
        private UUID id;
        private UUID mentorId;
        private UUID menteeId;
        private UUID skillId;
        private UUID companyProgramId;
        private UUID companyProgramParticipantId;
        private String title;
        private String description;
        private ZonedDateTime scheduledStart;
        private ZonedDateTime scheduledEnd;
        private Session.SessionStatus status;
        private Session.MeetingPlatform meetingPlatform;
        private String meetingUrl;
        private String meetingId;
        private String meetingPassword;
        private BigDecimal price;
        private String currency;
        private Session.PaymentStatus paymentStatus;
        private Boolean paymentRequired;
        private String menteeMessage;
        private Map<String, Object> questionnaireResponses;
        private String mentorResponse;
        private String calendarEventId;
        private LocalDateTime confirmedAt;
        private LocalDateTime cancelledAt;
        private String cancellationReason;
        private Session.CancelledBy cancelledBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        // Additional computed fields for UI
        private String skillName;
        private String mentorName;
        private String menteeName;
        private String companyProgramName;
        private SessionOutcomeDto outcome;
        private SessionProposalResponseDto activeProposal;
        private long durationMinutes;
        private boolean canBeModified;
        private boolean isFutureBooking;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionProposalResponseDto {
        private UUID id;
        private UUID sessionId;
        private SessionProposal.ProposalType proposalType;
        private SessionProposal.ProposalStatus status;
        private String mentorMessage;
        private String menteeResponse;
        private UUID acceptedSlotId;
        private LocalDateTime proposedAt;
        private LocalDateTime respondedAt;
        private LocalDateTime expiresAt;
        private List<SessionProposalSlotResponseDto> slots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionProposalSlotResponseDto {
        private UUID id;
        private ZonedDateTime scheduledStart;
        private ZonedDateTime scheduledEnd;
        private Integer sortOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSupportRequestResponseDto {
        private UUID id;
        private UUID sessionId;
        private SessionSupportRequest.RequesterType requesterType;
        private UUID requesterId;
        private String message;
        private SessionSupportRequest.SupportStatus status;
        private LocalDateTime createdAt;
    }
    
    /**
     * DTO for session summary (dashboard views)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummaryDto {
        private UUID id;
        private String skillName;
        private String partnerName; // mentor name for mentee, mentee name for mentor
        private ZonedDateTime sessionTime;
        private Session.SessionStatus status;
        private String meetingUrl;
        private long durationMinutes;
    }
    
    /**
     * DTO for session statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionStatsDto {
        private long totalSessions;
        private long pendingSessions;
        private long confirmedSessions;
        private long completedSessions;
        private long cancelledSessions;
        private BigDecimal totalEarnings;
        private BigDecimal averageSessionRating;
    }
    
    /**
     * DTO for time slot availability
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotDto {
        private ZonedDateTime startTime;
        private ZonedDateTime endTime;
        private boolean available;
        private String reason; // Why not available (if applicable)
    }
    
    /**
     * DTO for mentor availability query
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentorAvailabilityRequestDto {
        private UUID mentorId;
        private ZonedDateTime startDate;
        private ZonedDateTime endDate;
        private int sessionDurationMinutes;
    }

    /**
     * DTO for session booking error with payment requirement and recommended plans
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionBookingErrorDto {
        private String message;
        private Boolean paymentRequired;
        private Integer remainingSessions;
        private Integer addonSessionsRemaining;
        private SessionBookingEligibility.EligibilityReason reason;
        private List<RecommendedPlanDto> recommendedPlans;
        private AddOnSessionDto addOnOption;

        /**
         * Create error DTO from SessionBookingEligibility
         */
        public static SessionBookingErrorDto fromEligibility(SessionBookingEligibility eligibility) {
            return SessionBookingErrorDto.builder()
                    .message(eligibility.getMessage())
                    .paymentRequired(!eligibility.isCanBook())
                    .remainingSessions(eligibility.getSessionsRemaining())
                    .addonSessionsRemaining(eligibility.getAddonSessionsRemaining())
                    .reason(eligibility.getReason())
                    .recommendedPlans(eligibility.getRecommendedPlans())
                    .addOnOption(eligibility.getAddOnOption())
                    .build();
        }
    }

    /**
     * DTO for testing WhatsApp notifications
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestWhatsAppRequestDto {
        @NotNull(message = "Phone number is required")
        private String phoneNumber;

        private String templateName;

        private Map<String, String> templateParams;

        private List<String> bodyParameters;
    }
}
