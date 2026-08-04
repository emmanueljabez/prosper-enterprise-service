package com.prosper.prospermentor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "company_session_wallets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_subscription_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySessionWallet {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_subscription_id", nullable = false)
    private CompanySubscription companySubscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "price_per_session_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerSessionSnapshot = BigDecimal.ZERO;

    @Column(name = "sessions_purchased_total", nullable = false)
    private Integer sessionsPurchasedTotal = 0;

    @Column(name = "sessions_allocated_total", nullable = false)
    private Integer sessionsAllocatedTotal = 0;

    @Column(name = "sessions_returned_total", nullable = false)
    private Integer sessionsReturnedTotal = 0;

    @Column(name = "sessions_available", nullable = false)
    private Integer sessionsAvailable = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (pricePerSessionSnapshot == null) {
            pricePerSessionSnapshot = BigDecimal.ZERO;
        }
        if (sessionsPurchasedTotal == null) {
            sessionsPurchasedTotal = 0;
        }
        if (sessionsAllocatedTotal == null) {
            sessionsAllocatedTotal = 0;
        }
        if (sessionsReturnedTotal == null) {
            sessionsReturnedTotal = 0;
        }
        if (sessionsAvailable == null) {
            sessionsAvailable = 0;
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
