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

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

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
     * Whether a reusable card/token is available for automatic renewal.
     */
    @Column(name = "auto_renew_card_on_file", nullable = false)
    private Boolean autoRenewCardOnFile = false;

    /**
     * CyberSource payment token (if returned by gateway).
     */
    @Column(name = "auto_renew_payment_token", length = 255)
    private String autoRenewPaymentToken;

    /**
     * CyberSource customer token used for recurring charges.
     */
    @Column(name = "auto_renew_customer_token", length = 255)
    private String autoRenewCustomerToken;

    /**
     * CyberSource payment instrument ID used for recurring charges.
     */
    @Column(name = "auto_renew_payment_instrument_id", length = 255)
    private String autoRenewPaymentInstrumentId;

    /**
     * Card metadata for display/audit.
     */
    @Column(name = "auto_renew_card_type", length = 50)
    private String autoRenewCardType;

    @Column(name = "auto_renew_card_last_four", length = 4)
    private String autoRenewCardLastFour;

    @Column(name = "auto_renew_tokenized_at")
    private LocalDateTime autoRenewTokenizedAt;

    @Column(name = "auto_renew_last_charge_at")
    private LocalDateTime autoRenewLastChargeAt;

    @Column(name = "auto_renew_last_failure_reason", columnDefinition = "TEXT")
    private String autoRenewLastFailureReason;

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
        LocalDateTime now = LocalDateTime.now();
        boolean activeStatus = status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL;
        boolean started = startDate == null || !now.isBefore(startDate);
        boolean notEnded = endDate == null || !now.isAfter(endDate);
        return activeStatus && started && notEnded;
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

    public void decrementSessionsUsed() {
        if (plan != null && plan.isUnlimited()) {
            return;
        }
        if (this.sessionsUsed == null || this.sessionsUsed <= 0) {
            return;
        }
        this.sessionsUsed--;
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
        applyPaidRenewal();
    }

    /**
     * Apply one paid renewal cycle regardless of auto-renew toggle.
     * Used when renewal is triggered manually or fulfilled via invoice payment.
     */
    public void applyPaidRenewal() {
        LocalDateTime anchor = currentPeriodEnd != null
                ? currentPeriodEnd
                : (endDate != null ? endDate : LocalDateTime.now());
        LocalDateTime nextPeriodEnd = anchor.plusMonths(resolveBillingDurationMonths());

        this.currentPeriodStart = anchor;
        this.currentPeriodEnd = nextPeriodEnd;
        this.endDate = nextPeriodEnd;
        this.sessionsUsed = 0;
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * Check whether this subscription has sufficient card-on-file token data
     * for automatic CyberSource renewal attempts.
     */
    public boolean hasAutoRenewPaymentMethod() {
        if (!Boolean.TRUE.equals(autoRenewCardOnFile)) {
            return false;
        }
        return autoRenewCustomerToken != null && !autoRenewCustomerToken.isBlank()
                && autoRenewPaymentInstrumentId != null && !autoRenewPaymentInstrumentId.isBlank();
    }

    /**
     * Cancel subscription
     */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    private int resolveBillingDurationMonths() {
        if (plan == null) {
            return 1;
        }
        return plan.resolveDurationMonthsForInterval(billingInterval);
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
