package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request model for initiating a generic payment (events, top-ups, add-ons, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericPaymentRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Payment type is required")
    private String paymentType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    /**
     * Currency code for the amount (e.g., "USD", "KES", "UGX")
     * If not specified, defaults to USD
     * Amount will be converted to KES for Mpesa payment
     */
    private String currency;

    /**
     * Phone number for payment
     * Format: 254712345678 or 0712345678 or +254712345678
     */
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+?254|0)?[17]\\d{8}$", message = "Invalid phone number format")
    private String phoneNumber;

    @NotBlank(message = "Description is required")
    private String description;

    /**
     * Optional metadata in JSON format to store additional information
     * e.g., {"eventId":"evt-123","eventName":"Tech Conference 2025","attendeeType":"VIP"}
     */
    private String metadata;
}
