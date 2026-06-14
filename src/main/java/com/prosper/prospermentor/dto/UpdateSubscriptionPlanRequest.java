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

    private CreateSubscriptionPlanRequest.SubscriptionPlanAudience planAudience;

    private Integer sessionsPerPeriod;

    @Min(value = 1, message = "Duration must be at least 1 month")
    private Integer durationMonths;

    @Min(value = 0, message = "Yearly cost must be non-negative")
    private BigDecimal yearlyCost;

    private Boolean isActive;

    private Integer displayOrder;

    private String features;

    @Min(value = 1, message = "minSeats must be at least 1")
    private Integer minSeats;

    @Min(value = 1, message = "defaultSeats must be at least 1")
    private Integer defaultSeats;

    @Min(value = 1, message = "maxSeats must be at least 1")
    private Integer maxSeats;
}
