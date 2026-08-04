package com.prosper.prospermentor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * DTOs for MentorAvailability operations
 */
public class MentorAvailabilityDTO {

    /**
     * Request DTO for creating a new availability slot
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "Mentor ID is required")
        private UUID mentorId;

        @NotNull(message = "Day of week is required")
        private DayOfWeek dayOfWeek;

        @NotNull(message = "Start time is required")
        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @NotNull(message = "End time is required")
        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        @lombok.Builder.Default
        private Boolean isActive = true;
    }

    /**
     * Request DTO for updating an existing availability slot
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {

        private DayOfWeek dayOfWeek;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        private Boolean isActive;
    }

    /**
     * Request DTO for bulk creating multiple availability slots
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkCreateRequest {

        @NotNull(message = "Mentor ID is required")
        private UUID mentorId;

        @NotNull(message = "Availability slots are required")
        private java.util.List<TimeSlot> slots;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TimeSlot {
            @NotNull(message = "Day of week is required")
            private DayOfWeek dayOfWeek;

            @NotNull(message = "Start time is required")
            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime startTime;

            @NotNull(message = "End time is required")
            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime endTime;

            @lombok.Builder.Default
            private Boolean isActive = true;
        }
    }

    /**
     * Request DTO for bulk updating multiple availability slots
     * Replaces all existing availability for the mentor with new slots
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkUpdateRequest {

        @NotNull(message = "Mentor ID is required")
        private UUID mentorId;

        @NotNull(message = "Availability slots are required")
        private java.util.List<TimeSlot> slots;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TimeSlot {
            @NotNull(message = "Day of week is required")
            private DayOfWeek dayOfWeek;

            @NotNull(message = "Start time is required")
            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime startTime;

            @NotNull(message = "End time is required")
            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime endTime;

            @lombok.Builder.Default
            private Boolean isActive = true;
        }
    }

    /**
     * Response DTO containing availability slot details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Response {

        private Long id;
        private UUID mentorId;
        private DayOfWeek dayOfWeek;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        private Boolean isActive;
        private Long durationInMinutes;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private java.time.LocalDateTime createdAt;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private java.time.LocalDateTime updatedAt;
    }

    /**
     * Response DTO for listing availability grouped by day
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayAvailabilityResponse {
        private DayOfWeek dayOfWeek;
        private java.util.List<TimeSlotResponse> timeSlots;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TimeSlotResponse {
            private Long id;

            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime startTime;

            @JsonFormat(pattern = "HH:mm:ss")
            private LocalTime endTime;

            private Boolean isActive;
            private Long durationInMinutes;
        }
    }

    /**
     * Response DTO for mentor's weekly schedule
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyScheduleResponse {
        private UUID mentorId;
        private Long totalSlots;
        private Long activeSlots;
        private java.util.List<DayAvailabilityResponse> schedule;
    }

    /**
     * Request DTO for querying availability
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryRequest {
        private UUID mentorId;
        private DayOfWeek dayOfWeek;

        @lombok.Builder.Default
        private Boolean activeOnly = true;
    }
}
