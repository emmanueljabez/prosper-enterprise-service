package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feature entity representing individual features/benefits available in subscription plans
 * Examples: Network access, YouTube channel, Learn content, Summit recordings, etc.
 */
@Entity
@Table(name = "features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Unique code for programmatic access (e.g., "NETWORK", "YOUTUBE", "LEARN")
     */
    @NotBlank
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Display name of the feature
     */
    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Detailed description of the feature
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Type/category of feature
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private FeatureType type;

    /**
     * Whether this feature is active
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    /**
     * Feature type enumeration
     */
    public enum FeatureType {
        CONTENT_ACCESS,    // Access to content libraries (YouTube, Learn, etc.)
        MENTOR_SESSION,    // 1:1 or virtual mentor sessions
        EVENT_ACCESS,      // Summit, recordings, events
        COMMUNITY          // Network access, community features
    }
}
