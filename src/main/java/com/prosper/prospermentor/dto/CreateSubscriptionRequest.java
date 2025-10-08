package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request model for creating a new subscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    private boolean isTrial = false;

    /**
     * Phone number for payment (required for paid plans)
     * Format: 254712345678 or 0712345678 or +254712345678
     */
    @Pattern(regexp = "^(\\+?254|0)?[17]\\d{8}$", message = "Invalid phone number format")
    private String phoneNumber;
}
