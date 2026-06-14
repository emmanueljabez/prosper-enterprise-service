package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Exchange rate entity for multi-currency support
 * Stores conversion rates from base currency (USD) to target currencies
 */
@Entity
@Table(name = "exchange_rates", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"currency_code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Three-letter ISO 4217 currency code (e.g., "KES", "UGX", "TZS", "USD", "EUR")
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Currency name (e.g., "Kenyan Shilling", "US Dollar")
     */
    @Column(name = "currency_name", nullable = false)
    private String currencyName;

    /**
     * Exchange rate from USD to this currency
     * Example: If 1 USD = 130 KES, then rate = 130.0
     */
    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    /**
     * Whether this currency is active and available for use
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Whether this is the default currency (typically KES)
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /**
     * Currency symbol (e.g., "$", "KSh", "UGX")
     */
    @Column(name = "symbol", length = 10)
    private String symbol;

    /**
     * Last time this rate was updated
     */
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    /**
     * Source of the exchange rate (e.g., "MANUAL", "API", "CENTRAL_BANK")
     */
    @Column(name = "source", length = 50)
    private String source = "MANUAL";

    /**
     * When this record was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When this record was last modified
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Update the exchange rate
     */
    public void updateRate(BigDecimal newRate, String source) {
        this.rate = newRate;
        this.source = source;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Activate this currency
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Deactivate this currency
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Set as default currency
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * Remove default status
     */
    public void removeDefault() {
        this.isDefault = false;
    }
}
