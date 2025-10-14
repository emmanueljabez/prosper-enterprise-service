package com.prosper.prospermentor.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
     * @deprecated Use planFeatures relationship instead for structured feature management
     */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    /**
     * Billing type for this plan
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 20)
    private BillingType billingType = BillingType.RECURRING;

    /**
     * Yearly cost (for annual billing option)
     */
    @Column(name = "yearly_cost", precision = 10, scale = 2)
    private BigDecimal yearlyCost;

    /**
     * Whether this plan allows purchasing add-ons (like extra sessions)
     */
    @Column(name = "allows_addons", nullable = false)
    private Boolean allowsAddons = false;

    /**
     * Cost per add-on session (if applicable)
     */
    @Column(name = "addon_session_cost", precision = 10, scale = 2)
    private BigDecimal addonSessionCost;

    /**
     * Plan features - structured relationship with features
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<PlanFeature> planFeatures = new ArrayList<>();

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

    /**
     * Check if plan has a specific feature
     */
    public boolean hasFeature(String featureCode) {
        return planFeatures.stream()
            .anyMatch(pf -> pf.getFeature().getCode().equals(featureCode)
                         && pf.getEnabled()
                         && pf.isAvailable());
    }

    /**
     * Get the limit for a specific feature
     * Returns 0 if feature not found, -1 if unlimited, or the specific limit
     */
    public Integer getFeatureLimit(String featureCode) {
        return planFeatures.stream()
            .filter(pf -> pf.getFeature().getCode().equals(featureCode) && pf.getEnabled())
            .map(PlanFeature::getLimitValue)
            .findFirst()
            .orElse(0);
    }

    /**
     * Get a specific feature configuration
     */
    public PlanFeature getPlanFeature(String featureCode) {
        return planFeatures.stream()
            .filter(pf -> pf.getFeature().getCode().equals(featureCode))
            .findFirst()
            .orElse(null);
    }

    /**
     * Add a feature to this plan
     */
    public void addFeature(PlanFeature planFeature) {
        planFeatures.add(planFeature);
        planFeature.setPlan(this);
    }

    /**
     * Remove a feature from this plan
     */
    public void removeFeature(PlanFeature planFeature) {
        planFeatures.remove(planFeature);
        planFeature.setPlan(null);
    }

    /**
     * Billing type enumeration
     */
    public enum BillingType {
        RECURRING,    // Monthly/yearly recurring subscription
        ONE_TIME,     // One-time payment (like Summit access)
        FREE          // Free tier (no billing)
    }
}
