package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request model for purchasing add-on sessions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAddonRequest {

    @NotNull(message = "User ID is required")
    private String userId;

    @NotNull(message = "Quantity is required")
    private Integer quantity;

    /**
     * Phone number for M-Pesa payment
     * Format: 254712345678 or 0712345678 or +254712345678
     */
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    /**
     * Preferred currency for payment (e.g., "KES", "UGX", "USD")
     * If not provided, defaults to KES
     */
    private String currency;
}
