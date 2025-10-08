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
 * DTOs for booking-related API operations
 * Separate DTOs for request/response to follow API design best practices
 */
public class BookingDtos {
    
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
     * Request DTO for creating a booking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBookingRequestDto {
        
        @NotNull(message = "Mentor ID is required")
        private UUID mentorId;
        
        @NotNull(message = "Mentee ID is required")
        private UUID menteeId;
        
        @NotNull(message = "Skill ID is required")
        private UUID skillId;
        
        @NotNull(message = "Requested start time is required")
        private ZonedDateTime requestedStartTime;
        
        @NotNull(message = "Requested end time is required")
        private ZonedDateTime requestedEndTime;
        
        @NotNull(message = "Meeting platform is required")
        private Session.MeetingPlatform meetingPlatform;
        
        private String menteeMessage;
    }
    
    /**
     * Request DTO for confirming a booking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmBookingRequestDto {
        private String mentorResponse;
    }
    
    /**
     * Request DTO for cancelling a booking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelBookingRequestDto {
        
        @NotNull(message = "Cancelled by is required")
        private Session.CancelledBy cancelledBy;
        
        private String reason;
    }
    
    /**
     * Response DTO for booking information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingResponseDto {
        private UUID id;
        private UUID mentorId;
        private UUID menteeId;
        private UUID skillId;
        private ZonedDateTime requestedStartTime;
        private ZonedDateTime requestedEndTime;
        private Session.MeetingPlatform meetingPlatform;
        private String menteeMessage;
        private Session.SessionStatus status;
        private String meetingUrl;
        private String meetingId;
        private String meetingPassword;
        private BigDecimal price;
        private String currency;
        private Session.PaymentStatus paymentStatus;
        private String mentorResponse;
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
     * DTO for booking summary (dashboard views)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingSummaryDto {
        private UUID id;
        private String skillName;
        private String partnerName; // mentor name for mentee, mentee name for mentor
        private ZonedDateTime sessionTime;
        private Session.SessionStatus status;
        private String meetingUrl;
        private long durationMinutes;
    }
    
    /**
     * DTO for booking statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingStatsDto {
        private long totalBookings;
        private long pendingBookings;
        private long confirmedBookings;
        private long completedBookings;
        private long cancelledBookings;
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
    public static class AvailabilityRequestDto {
        private UUID mentorId;
        private ZonedDateTime startDate;
        private ZonedDateTime endDate;
        private int sessionDurationMinutes;
    }
}
