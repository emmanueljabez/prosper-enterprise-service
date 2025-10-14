package com.prosper.prospermentor.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PlanFeature entity representing the junction between SubscriptionPlan and Feature
 * Defines which features are included in each plan and their limits
 */
@Entity
@Table(name = "plan_features",
    uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "feature_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * The subscription plan
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    @JsonBackReference
    private SubscriptionPlan plan;

    /**
     * The feature included in this plan
     */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    /**
     * Limit for this feature
     * -1 = unlimited
     * 0 = not available
     * N = specific quantity/limit
     */
    @NotNull
    @Column(name = "limit_value", nullable = false)
    private Integer limitValue = 0;

    /**
     * Whether this feature is currently enabled in the plan
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * Additional metadata about the feature in JSON format
     * Can store things like: {"quality": "HD", "downloads": true}
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Business Logic Methods

    /**
     * Check if this feature is unlimited
     */
    public boolean isUnlimited() {
        return limitValue == -1;
    }

    /**
     * Check if this feature is available
     */
    public boolean isAvailable() {
        return enabled && (limitValue > 0 || isUnlimited());
    }

    /**
     * Check if feature has a specific limit
     */
    public boolean hasLimit() {
        return limitValue > 0 && !isUnlimited();
    }
}
