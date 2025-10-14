package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request model for upgrading a subscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpgradeSubscriptionRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "New plan ID is required")
    private UUID newPlanId;

    @NotNull(message = "Phone number is required for payment")
    private String phoneNumber;
}
