package com.prosper.prospermentor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Invoice entity used by the unified payment page.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 64)
    private String invoiceNumber;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "redirect_success_url", columnDefinition = "TEXT")
    private String redirectSuccessUrl;

    @Column(name = "redirect_cancel_url", columnDefinition = "TEXT")
    private String redirectCancelUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = InvoiceStatus.OPEN;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markAsPaid() {
        this.status = InvoiceStatus.PAID;
        if (this.paidAt == null) {
            this.paidAt = LocalDateTime.now();
        }
    }

    public void markAsExpired() {
        if (this.status == InvoiceStatus.OPEN) {
            this.status = InvoiceStatus.EXPIRED;
        }
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isPayable() {
        if (status != InvoiceStatus.OPEN) {
            return false;
        }
        return !isExpired();
    }

    public enum InvoiceStatus {
        DRAFT,
        OPEN,
        PAID,
        EXPIRED,
        VOID,
        FAILED
    }
}
