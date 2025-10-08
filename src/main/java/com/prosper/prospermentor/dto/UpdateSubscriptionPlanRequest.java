package com.prosper.prospermentor.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request model for updating a subscription plan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionPlanRequest {

    private String name;

    private String description;

    @Min(value = 0, message = "Cost must be non-negative")
    private BigDecimal cost;

    private String currency;

    private Integer sessionsPerPeriod;

    @Min(value = 1, message = "Duration must be at least 1 month")
    private Integer durationMonths;

    private Boolean isActive;

    private Integer displayOrder;

    private String features;
}
