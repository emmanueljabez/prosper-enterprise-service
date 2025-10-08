package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Subscription entity for tracking user subscription plans
 * Maps to the subscriptions table in Supabase
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    /**
     * Primary key - UUID with auto-generation
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * User ID - foreign key to users/profiles
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Subscription plan - foreign key to subscription_plans
     */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    /**
     * Number of sessions allowed per month (cached from plan)
     */
    @Column(name = "sessions_per_month", nullable = false)
    private Integer sessionsPerMonth;

    /**
     * Number of sessions used in current period
     */
    @Column(name = "sessions_used", nullable = false)
    private Integer sessionsUsed = 0;

    /**
     * Subscription start date
     */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /**
     * Subscription end date
     */
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    /**
     * Current billing period start
     */
    @Column(name = "current_period_start", nullable = false)
    private LocalDateTime currentPeriodStart;

    /**
     * Current billing period end
     */
    @Column(name = "current_period_end", nullable = false)
    private LocalDateTime currentPeriodEnd;

    /**
     * Subscription status
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /**
     * Whether subscription auto-renews
     */
    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    /**
     * Whether this is a trial subscription
     */
    @Column(name = "is_trial", nullable = false)
    private Boolean isTrial = false;

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
     * Check if subscription has available sessions
     */
    public boolean hasAvailableSessions() {
        if (plan != null && plan.isUnlimited()) {
            return isActive();
        }
        return isActive() && sessionsUsed < sessionsPerMonth;
    }

    /**
     * Check if subscription is currently active
     */
    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE &&
               LocalDateTime.now().isBefore(endDate) &&
               LocalDateTime.now().isAfter(startDate);
    }

    /**
     * Check if subscription is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate);
    }

    /**
     * Increment sessions used
     */
    public void incrementSessionsUsed() {
        // Don't increment for unlimited plans
        if (plan != null && plan.isUnlimited()) {
            return;
        }
        if (!hasAvailableSessions()) {
            throw new IllegalStateException("No available sessions in subscription");
        }
        this.sessionsUsed++;
    }

    /**
     * Reset sessions used (for new billing period)
     */
    public void resetSessionsUsed() {
        this.sessionsUsed = 0;
    }

    /**
     * Renew subscription for next period
     */
    public void renew() {
        if (!autoRenew) {
            throw new IllegalStateException("Auto-renew is disabled for this subscription");
        }

        this.currentPeriodStart = this.currentPeriodEnd;
        this.currentPeriodEnd = this.currentPeriodEnd.plusMonths(1);
        this.endDate = this.currentPeriodEnd;
        this.sessionsUsed = 0;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * Cancel subscription
     */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    /**
     * Get remaining sessions
     */
    public int getRemainingSessionsCount() {
        return Math.max(0, sessionsPerMonth - sessionsUsed);
    }

    // Enums

    /**
     * Subscription status enumeration
     */
    public enum SubscriptionStatus {
        PENDING_PAYMENT,  // Waiting for payment confirmation
        ACTIVE,           // Active subscription
        EXPIRED,          // Subscription expired
        CANCELLED,        // Cancelled by user
        SUSPENDED,        // Suspended (payment failed)
        TRIAL             // Trial period
    }
}