package com.prosper.prospermentor.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SubscriptionAddon entity for tracking add-on purchases
 * Examples: Extra 1:1 sessions purchased beyond the subscription limit
 */
@Entity
@Table(name = "subscription_addons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * The subscription this addon belongs to
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    @JsonIgnoreProperties({"plan", "addons", "hibernateLazyInitializer", "handler"})
    private Subscription subscription;

    /**
     * Type of addon (e.g., "EXTRA_SESSION", "PREMIUM_CONTENT")
     */
    @NotNull
    @Column(name = "addon_type", nullable = false, length = 50)
    private String addonType;

    /**
     * Display name of the addon
     */
    @Column(name = "addon_name", length = 100)
    private String addonName;

    /**
     * Quantity purchased
     */
    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Number of units used/consumed
     */
    @Column(name = "used", nullable = false)
    private Integer used = 0;

    /**
     * Total cost paid for this addon
     */
    @NotNull
    @Column(name = "total_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost;

    /**
     * Currency code
     */
    @Column(name = "currency", length = 3)
    private String currency = "KES";

    /**
     * When the addon was purchased
     */
    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    /**
     * When the addon expires (optional)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Status of the addon
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AddonStatus status = AddonStatus.ACTIVE;

    /**
     * Payment ID associated with this addon purchase
     */
    @Column(name = "payment_id")
    private UUID paymentId;

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
        if (purchasedAt == null) {
            purchasedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Business Logic Methods

    /**
     * Check if addon has remaining units
     */
    public boolean hasRemaining() {
        return status == AddonStatus.ACTIVE && used < quantity &&
               (expiresAt == null || LocalDateTime.now().isBefore(expiresAt));
    }

    /**
     * Get remaining units
     */
    public int getRemaining() {
        if (!hasRemaining()) {
            return 0;
        }
        return quantity - used;
    }

    /**
     * Consume one unit from the addon
     */
    public void consumeUnit() {
        if (!hasRemaining()) {
            throw new IllegalStateException("No remaining units in addon");
        }
        this.used++;

        // Auto-complete when fully used
        if (this.used >= this.quantity) {
            this.status = AddonStatus.EXHAUSTED;
        }
    }

    /**
     * Return one consumed unit to the add-on after a booking is declined by the mentor.
     */
    public void restoreUnit() {
        if (this.used == null || this.used <= 0) {
            return;
        }

        this.used--;
        if (this.status == AddonStatus.EXHAUSTED && this.used < this.quantity) {
            this.status = AddonStatus.ACTIVE;
        }
    }

    /**
     * Check if addon is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if addon is active
     */
    public boolean isActive() {
        return status == AddonStatus.ACTIVE && hasRemaining();
    }

    /**
     * Addon status enumeration
     */
    public enum AddonStatus {
        ACTIVE,      // Active and available for use
        EXHAUSTED,   // All units have been consumed
        EXPIRED,     // Expired by time
        CANCELLED,   // Cancelled/refunded
        SUSPENDED    // Temporarily suspended
    }
}
