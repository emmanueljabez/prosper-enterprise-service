package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MentorProfile entity representing mentor profile information
 * Maps to the mentor_profiles table in Supabase
 */
@Entity
@Table(name = "mentor_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfile {

    /**
     * Primary key - UUID that also serves as foreign key to profiles table
     */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Professional title
     */
    @Column(name = "title")
    private String title;

    /**
     * Company name
     */
    @Column(name = "company")
    private String company;

    /**
     * Years of professional experience
     */
    @Column(name = "years_experience")
    private Integer yearsExperience;

    /**
     * Hourly rate for mentoring sessions
     */
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    /**
     * List of specializations
     * Stored as text array in PostgreSQL
     */
    @Column(name = "specializations", columnDefinition = "text[]")
    private List<String> specializations;

    /**
     * List of languages spoken
     * Stored as text array in PostgreSQL
     */
    @Column(name = "languages", columnDefinition = "text[]")
    private List<String> languages;

    /**
     * Timezone
     */
    @Column(name = "timezone")
    private String timezone;

    /**
     * Availability hours stored as JSONB
     */
    @Column(name = "availability_hours", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String availabilityHours;

    /**
     * Total number of sessions conducted
     */
    @Column(name = "total_sessions")
    private Integer totalSessions = 0;

    /**
     * Average rating from mentees
     */
    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating = BigDecimal.ZERO;

    /**
     * Total number of reviews received
     */
    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    /**
     * Whether the mentor is currently available for new sessions
     */
    @Column(name = "is_available")
    private Boolean isAvailable = true;

    /**
     * Mentor's biography
     */
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    /**
     * Profile picture URL
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
