package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entity representing mentor availability schedules
 * A mentor can define multiple time slots across different days of the week
 */
@Entity
@Table(
    name = "mentor_availability",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_mentor_day_time",
            columnNames = {"mentor_id", "day_of_week", "start_time", "end_time"}
        )
    },
    indexes = {
        @Index(name = "idx_mentor_availability_mentor_id", columnList = "mentor_id"),
        @Index(name = "idx_mentor_availability_day", columnList = "day_of_week"),
        @Index(name = "idx_mentor_availability_active", columnList = "is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MentorAvailability extends BaseEntity {

    /**
     * Reference to the mentor (User with MENTOR role)
     * Stored as UUID to match Supabase user ID
     */
    @NotNull(message = "Mentor ID is required")
    @Column(name = "mentor_id", nullable = false, columnDefinition = "UUID")
    private UUID mentorId;

    /**
     * Day of the week for this availability slot
     */
    @NotNull(message = "Day of week is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    /**
     * Start time of the availability slot
     */
    @NotNull(message = "Start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * End time of the availability slot
     */
    @NotNull(message = "End time is required")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * Whether this availability slot is currently active
     * Mentors can temporarily disable slots without deleting them
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Validate that end time is after start time
     */
    @PrePersist
    @PreUpdate
    private void validateTimeRange() {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
    }

    /**
     * Check if this slot is available (active)
     */
    public boolean isAvailable() {
        return isActive != null && isActive;
    }

    /**
     * Get duration in minutes
     */
    public long getDurationInMinutes() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMinutes();
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("MentorAvailability{id=%d, mentorId=%s, dayOfWeek=%s, startTime=%s, endTime=%s, isActive=%s}",
                getId(), mentorId, dayOfWeek, startTime, endTime, isActive);
    }
}
