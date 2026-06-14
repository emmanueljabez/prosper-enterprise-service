package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_subscription_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySubscriptionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_subscription_id", nullable = false)
    private CompanySubscription companySubscription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompanySubscriptionMemberStatus status = CompanySubscriptionMemberStatus.ACTIVE;

    @Column(name = "sessions_used", nullable = false)
    private Integer sessionsUsed = 0;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (assignedAt == null) {
            assignedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == CompanySubscriptionMemberStatus.ACTIVE
                && companySubscription != null
                && companySubscription.isActive();
    }

    public boolean hasAvailableSessions() {
        if (!isActive() || companySubscription == null || companySubscription.getPlan() == null) {
            return false;
        }
        if (companySubscription.getPlan().isUnlimited()) {
            return true;
        }
        Integer sessionsPerPeriod = companySubscription.getPlan().getSessionsPerPeriod();
        int allowed = sessionsPerPeriod != null ? sessionsPerPeriod : 0;
        int used = sessionsUsed != null ? sessionsUsed : 0;
        return used < allowed;
    }

    public int getRemainingSessionsCount() {
        if (companySubscription == null || companySubscription.getPlan() == null) {
            return 0;
        }
        if (companySubscription.getPlan().isUnlimited()) {
            return Integer.MAX_VALUE;
        }
        int allowed = companySubscription.getPlan().getSessionsPerPeriod() != null
                ? companySubscription.getPlan().getSessionsPerPeriod()
                : 0;
        int used = sessionsUsed != null ? sessionsUsed : 0;
        return Math.max(0, allowed - used);
    }

    public void incrementSessionsUsed() {
        if (!hasAvailableSessions()) {
            throw new IllegalStateException("No available corporate sessions");
        }
        this.sessionsUsed = (sessionsUsed != null ? sessionsUsed : 0) + 1;
    }

    public void resetSessionsUsed() {
        this.sessionsUsed = 0;
    }

    public void revoke() {
        this.status = CompanySubscriptionMemberStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
    }

    public void activate(UUID assignedByUserId) {
        this.status = CompanySubscriptionMemberStatus.ACTIVE;
        this.assignedByUserId = assignedByUserId;
        this.assignedAt = LocalDateTime.now();
        this.revokedAt = null;
        this.sessionsUsed = 0;
    }

    public enum CompanySubscriptionMemberStatus {
        ACTIVE,
        REVOKED
    }
}
