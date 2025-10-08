package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SubscriptionPlan entity for managing subscription plans
 * Maps to the subscription_plans table
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {

    /**
     * Primary key - UUID with auto-generation
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Plan name (e.g., "Free", "Basic", "Premium")
     */
    @NotBlank
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    /**
     * Plan code for programmatic access (e.g., "FREE", "BASIC", "PREMIUM")
     */
    @NotBlank
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Plan description/details
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Plan cost in currency units
     */
    @NotNull
    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    /**
     * Currency code (e.g., "KES", "USD")
     */
    @NotBlank
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    /**
     * Number of sessions allowed per billing period
     * -1 indicates unlimited sessions
     */
    @NotNull
    @Column(name = "sessions_per_period", nullable = false)
    private Integer sessionsPerPeriod;

    /**
     * Duration in months
     */
    @NotNull
    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths = 1;

    /**
     * Whether this plan is currently active/available
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Display order for sorting plans
     */
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    /**
     * Additional features or benefits (JSON or comma-separated)
     */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

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

    // Business Logic Methods

    /**
     * Check if this plan offers unlimited sessions
     */
    public boolean isUnlimited() {
        return sessionsPerPeriod == -1;
    }

    /**
     * Check if this is a free plan
     */
    public boolean isFree() {
        return cost.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Get formatted price
     */
    public String getFormattedPrice() {
        if (isFree()) {
            return "Free";
        }
        return String.format("%s %s", currency, cost);
    }
}
