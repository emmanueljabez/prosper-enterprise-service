package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "seats_purchased", nullable = false)
    private Integer seatsPurchased;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CompanySubscriptionStatus status = CompanySubscriptionStatus.PENDING_PAYMENT;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 20)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = false;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "latest_invoice_id")
    private UUID latestInvoiceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        boolean activeStatus = status == CompanySubscriptionStatus.ACTIVE;
        boolean started = startDate == null || !now.isBefore(startDate);
        boolean notEnded = endDate == null || !now.isAfter(endDate);
        return activeStatus && started && notEnded;
    }

    public void activateNewPeriod() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = now.plusMonths(resolveDurationMonths());
        this.status = CompanySubscriptionStatus.ACTIVE;
        this.startDate = now;
        this.endDate = periodEnd;
        this.currentPeriodStart = now;
        this.currentPeriodEnd = periodEnd;
    }

    public void applyPaidRenewal() {
        LocalDateTime anchor = currentPeriodEnd != null
                ? currentPeriodEnd
                : (endDate != null ? endDate : LocalDateTime.now());
        LocalDateTime nextPeriodEnd = anchor.plusMonths(resolveDurationMonths());
        this.status = CompanySubscriptionStatus.ACTIVE;
        if (startDate == null) {
            startDate = anchor;
        }
        this.currentPeriodStart = anchor;
        this.currentPeriodEnd = nextPeriodEnd;
        this.endDate = nextPeriodEnd;
    }

    public void cancel() {
        this.status = CompanySubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    public void activateCorporateWalletSubscription() {
        LocalDateTime now = LocalDateTime.now();
        this.status = CompanySubscriptionStatus.ACTIVE;
        if (this.startDate == null) {
            this.startDate = now;
        }
        if (this.currentPeriodStart == null) {
            this.currentPeriodStart = this.startDate;
        }
        this.currentPeriodEnd = null;
        this.endDate = null;
    }

    private int resolveDurationMonths() {
        if (plan == null) {
            return 1;
        }
        return plan.resolveDurationMonthsForInterval(billingInterval);
    }

    public enum CompanySubscriptionStatus {
        PENDING_PAYMENT,
        ACTIVE,
        EXPIRED,
        CANCELLED,
        SUSPENDED
    }
}
