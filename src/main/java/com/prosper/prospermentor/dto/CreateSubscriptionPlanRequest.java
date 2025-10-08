package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request model for creating a new subscription plan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubscriptionPlanRequest {

    @NotBlank(message = "Plan name is required")
    private String name;

    @NotBlank(message = "Plan code is required")
    private String code;

    private String description;

    @NotNull(message = "Cost is required")
    @Min(value = 0, message = "Cost must be non-negative")
    private BigDecimal cost;

    @NotBlank(message = "Currency is required")
    private String currency = "KES";

    @NotNull(message = "Sessions per period is required")
    private Integer sessionsPerPeriod;

    @NotNull(message = "Duration in months is required")
    @Min(value = 1, message = "Duration must be at least 1 month")
    private Integer durationMonths = 1;

    private Boolean isActive = true;

    private Integer displayOrder = 0;

    private String features;
}
