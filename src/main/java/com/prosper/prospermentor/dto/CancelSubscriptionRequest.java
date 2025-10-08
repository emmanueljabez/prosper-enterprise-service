package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request model for cancelling a subscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelSubscriptionRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;
}
