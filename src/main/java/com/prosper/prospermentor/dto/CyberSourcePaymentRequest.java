package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for initiating CyberSource card payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CyberSourcePaymentRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Payment type is required")
    private String paymentType; // SUBSCRIPTION, ADDON, SESSION_BOOKING

    /**
     * Plan ID (for subscription payments)
     */
    private UUID planId;

    /**
     * Session ID (for session payments)
     */
    private UUID sessionId;

    /**
     * Subscription ID (for subscription upgrades)
     */
    private UUID subscriptionId;

    /**
     * Addon quantity (for addon purchases)
     */
    private Integer addonQuantity;

    /**
     * Item ID (generic - for subscription plan, addon, etc.)
     */
    private UUID itemId;

    /**
     * Optional custom description
     */
    private String description;

    /**
     * Optional custom return URL (defaults to configured URL)
     */
    private String returnUrl;

    /**
     * Optional custom cancel URL (defaults to configured URL)
     */
    private String cancelUrl;
}
