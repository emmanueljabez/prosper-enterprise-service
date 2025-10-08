package com.prosper.prospermentor.controller.dto;

import com.prosper.prospermentor.entity.Session;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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
     * Request DTO for creating a session
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionRequestDto {
        
        @NotNull(message = "Mentor ID is required")
        private UUID mentorId;
        
        @NotNull(message = "Mentee ID is required")
        private UUID menteeId;
        
        @NotNull(message = "Skill ID is required")
        private UUID skillId;
        
        @NotNull(message = "Scheduled start time is required")
        private ZonedDateTime scheduledStart;
        
        @NotNull(message = "Meeting platform is required")
        private Session.MeetingPlatform meetingPlatform;
        
        private String menteeMessage;
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
        private String menteeMessage;
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
        private long durationMinutes;
        private boolean canBeModified;
        private boolean isFutureBooking;
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
}
