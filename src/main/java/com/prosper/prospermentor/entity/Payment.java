package com.prosper.prospermentor.entity;

import com.prosper.prospermentor.entity.converter.PaymentStatusConverter;
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
 * Payment entity for tracking Mpesa transactions
 * Maps to the payments table in Supabase
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /**
     * Primary key - UUID with auto-generation
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Profile ID of the payer (the person who initiated the payment)
     */
    @Column(name = "payer_id", nullable = false)
    private UUID payerId;

    /**
     * Profile ID of the recipient (who receives the funds)
     */
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /**
     * User ID making the payment
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Session ID (if payment is for a session)
     */
    @Column(name = "session_id")
    private UUID sessionId;

    /**
     * Subscription ID (if payment is for subscription)
     */
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    /**
     * Payment type
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    /**
     * Payment amount
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Currency code (KES, USD, etc.)
     */
    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "KES";

    /**
     * Phone number used for Mpesa payment
     */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * Mpesa checkout request ID
     */
    @Column(name = "checkout_request_id", length = 100)
    private String checkoutRequestId;

    /**
     * Mpesa merchant request ID
     */
    @Column(name = "merchant_request_id", length = 100)
    private String merchantRequestId;

    /**
     * Mpesa transaction ID (receipt number)
     */
    @Column(name = "mpesa_receipt_number", length = 50)
    private String mpesaReceiptNumber;

    /**
     * Transaction date from Mpesa
     */
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    /**
     * Payment status
     */
    @NotNull
    @Convert(converter = PaymentStatusConverter.class)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Payment method
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.MPESA;

    /**
     * Payment description
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Mpesa result description
     */
    @Column(name = "result_description", length = 500)
    private String resultDescription;

    /**
     * Mpesa result code
     */
    @Column(name = "result_code")
    private Integer resultCode;

    /**
     * Number of retry attempts
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Last error message
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * Metadata in JSON format
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Audit fields
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (payerId == null) {
            payerId = userId;
        }
        if (recipientId == null) {
            recipientId = userId;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (payerId == null) {
            payerId = userId;
        }
        if (recipientId == null) {
            recipientId = userId;
        }
        updatedAt = LocalDateTime.now();
    }

    // Business Logic Methods

    /**
     * Mark payment as successful
     */
    public void markAsSuccessful(String mpesaReceiptNumber, LocalDateTime transactionDate) {
        this.status = PaymentStatus.COMPLETED;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
        this.transactionDate = transactionDate;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark payment as failed
     */
    public void markAsFailed(String errorMessage, Integer resultCode) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.resultCode = resultCode;
    }

    /**
     * Mark payment as cancelled
     */
    public void cancel() {
        if (this.status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed payment");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Check if payment is completed
     */
    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    /**
     * Check if payment can be retried
     */
    public boolean canRetry() {
        return status == PaymentStatus.FAILED && retryCount < 3;
    }

    // Enums

    /**
     * Payment type enumeration
     */
    public enum PaymentType {
        SESSION_BOOKING,    // Payment for individual session
        SUBSCRIPTION,       // Payment for subscription
        UPGRADE,           // Payment for subscription upgrade (prorated)
        ADDON,             // Payment for subscription add-ons (e.g., extra sessions)
        TOP_UP,            // Account top-up
        REFUND             // Refund payment
    }

    /**
     * Payment status enumeration
     */
    public enum PaymentStatus {
        PENDING,           // Payment initiated, waiting for confirmation
        COMPLETED,         // Payment successful
        FAILED,            // Payment failed
        CANCELLED,         // Payment cancelled
        REFUNDED          // Payment refunded
    }

    /**
     * Payment method enumeration
     */
    public enum PaymentMethod {
        MPESA,            // Mpesa payment
        CARD,             // Card payment
        BANK_TRANSFER,    // Bank transfer
        PAYPAL,           // PayPal
        FREE              // Free (subscription trial, etc.)
    }
}
