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
 * Entity for tracking detailed CyberSource transaction data
 * Maps to cybersource_transactions table in the database
 */
@Entity
@Table(name = "cybersource_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CyberSourceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Reference to the main payment record
     */
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    private Payment payment;

    /**
     * CyberSource transaction ID (returned in response)
     */
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    /**
     * Unique transaction UUID generated for this request
     */
    @Column(name = "transaction_uuid", nullable = false)
    private String transactionUuid;

    /**
     * CyberSource request ID
     */
    @Column(name = "request_id")
    private String requestId;

    // Authorization details

    /**
     * Authorization code from CyberSource
     */
    @Column(name = "auth_code")
    private String authCode;

    /**
     * Authorization amount
     */
    @Column(name = "auth_amount", precision = 10, scale = 2)
    private BigDecimal authAmount;

    /**
     * Authorization response code
     */
    @Column(name = "auth_response", length = 10)
    private String authResponse;

    /**
     * Authorization timestamp
     */
    @Column(name = "auth_time")
    private LocalDateTime authTime;

    // Card details (masked - CyberSource returns masked values)

    /**
     * Card type (Visa, Mastercard, Amex, Discover, etc.)
     */
    @Column(name = "card_type", length = 50)
    private String cardType;

    /**
     * Masked card number (e.g., ************1111)
     */
    @Column(name = "card_number", length = 20)
    private String cardNumber;

    /**
     * Card expiry (masked format)
     */
    @Column(name = "card_expiry", length = 10)
    private String cardExpiry;

    // Transaction details

    /**
     * Transaction amount
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Transaction currency (KES, USD, etc.)
     */
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    /**
     * CyberSource decision (ACCEPT, DECLINE, REVIEW, ERROR, CANCEL)
     */
    @Column(name = "decision", length = 20, nullable = false)
    private String decision;

    /**
     * CyberSource reason code
     */
    @Column(name = "reason_code", length = 10)
    private String reasonCode;

    /**
     * Response message from CyberSource
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Request data (for tracking and debugging)

    /**
     * Request reference number
     */
    @Column(name = "req_reference_number")
    private String reqReferenceNumber;

    /**
     * Billing first name
     */
    @Column(name = "req_bill_to_forename", length = 100)
    private String reqBillToForename;

    /**
     * Billing last name
     */
    @Column(name = "req_bill_to_surname", length = 100)
    private String reqBillToSurname;

    /**
     * Billing email
     */
    @Column(name = "req_bill_to_email")
    private String reqBillToEmail;

    // Signature verification data

    /**
     * List of signed field names
     */
    @Column(name = "signed_field_names", columnDefinition = "TEXT")
    private String signedFieldNames;

    /**
     * Request/Response signature
     */
    @Column(name = "signature", length = 512)
    private String signature;

    // Raw data for debugging and audit trail

    /**
     * Full raw request data (JSON)
     */
    @Column(name = "raw_request", columnDefinition = "TEXT")
    private String rawRequest;

    /**
     * Full raw response data (JSON)
     */
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    // Audit fields

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
     * Check if transaction was approved by CyberSource
     */
    public boolean isApproved() {
        return "ACCEPT".equalsIgnoreCase(decision);
    }

    /**
     * Check if transaction was declined
     */
    public boolean isDeclined() {
        return "DECLINE".equalsIgnoreCase(decision);
    }

    /**
     * Check if transaction requires manual review
     */
    public boolean requiresReview() {
        return "REVIEW".equalsIgnoreCase(decision);
    }

    /**
     * Check if transaction had an error
     */
    public boolean hasError() {
        return "ERROR".equalsIgnoreCase(decision);
    }

    /**
     * Check if transaction was cancelled
     */
    public boolean isCancelled() {
        return "CANCEL".equalsIgnoreCase(decision);
    }

    /**
     * Get user-friendly decision status
     */
    public String getFriendlyStatus() {
        if (isApproved()) {
            return "Approved";
        } else if (isDeclined()) {
            return "Declined";
        } else if (requiresReview()) {
            return "Under Review";
        } else if (isCancelled()) {
            return "Cancelled";
        } else {
            return "Error";
        }
    }

    /**
     * Get last 4 digits of card from masked card number
     */
    public String getCardLastFour() {
        if (cardNumber != null && cardNumber.length() >= 4) {
            return cardNumber.substring(cardNumber.length() - 4);
        }
        return null;
    }
}
