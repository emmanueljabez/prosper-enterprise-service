package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for recommended subscription plan information
 * Used to suggest upgrade paths to users who cannot book sessions
 *
 * These plans are specifically filtered to include ONLY plans that:
 * - Support one-on-one mentor session bookings (MENTOR_SESSION feature)
 * - Are currently active and available for purchase
 * - Have the mentor session feature enabled and available
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedPlanDto {

    /**
     * Plan ID
     */
    private UUID id;

    /**
     * Plan name (e.g., "Premium", "Professional")
     */
    private String name;

    /**
     * Plan code
     */
    private String code;

    /**
     * Plan description
     */
    private String description;

    /**
     * Monthly cost
     */
    private BigDecimal cost;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Number of sessions included per period (-1 for unlimited)
     */
    private Integer sessionsPerPeriod;

    /**
     * Display order (lower numbers shown first)
     */
    private Integer displayOrder;

    /**
     * Get formatted price for display
     */
    public String getFormattedPrice() {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return "Free";
        }
        return String.format("%s %s/month", currency, cost);
    }

    /**
     * Get sessions description
     */
    public String getSessionsDescription() {
        if (sessionsPerPeriod == -1) {
            return "Unlimited sessions";
        }
        return String.format("%d sessions per month", sessionsPerPeriod);
    }
}
