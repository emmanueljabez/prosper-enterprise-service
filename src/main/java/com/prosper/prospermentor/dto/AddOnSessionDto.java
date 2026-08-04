package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for add-on session purchase information
 * Used to suggest add-on session purchases to users who have exhausted their plan sessions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnSessionDto {

    /**
     * Cost per add-on session
     */
    private BigDecimal costPerSession;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Whether add-ons are available for this plan
     */
    private Boolean available;

    /**
     * Recommended number of sessions to purchase
     */
    private Integer recommendedQuantity;

    /**
     * Message about add-on availability
     */
    private String message;

    /**
     * Get formatted price for display
     */
    public String getFormattedPrice() {
        if (costPerSession == null) {
            return "N/A";
        }
        return String.format("%s %s per session", currency, costPerSession);
    }

    /**
     * Create an available add-on option
     */
    public static AddOnSessionDto available(BigDecimal cost, String currency, Integer recommendedQuantity) {
        return AddOnSessionDto.builder()
                .costPerSession(cost)
                .currency(currency)
                .available(true)
                .recommendedQuantity(recommendedQuantity)
                .message(String.format("Purchase additional sessions for %s %s each", currency, cost))
                .build();
    }

    /**
     * Create an unavailable add-on option
     */
    public static AddOnSessionDto unavailable(String message) {
        return AddOnSessionDto.builder()
                .available(false)
                .message(message)
                .build();
    }
}
