package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for CyberSource payment initiation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CyberSourcePaymentResponse {

    /**
     * Success indicator
     */
    private boolean success;

    /**
     * Response message
     */
    private String message;

    /**
     * Payment ID (our internal payment record)
     */
    private UUID paymentId;

    /**
     * Transaction UUID (for tracking)
     */
    private String transactionId;

    /**
     * CyberSource form endpoint URL
     */
    private String cybersourceEndpoint;

    /**
     * CyberSource form parameters (to be posted)
     * Includes all fields needed for hosted checkout
     */
    private Map<String, String> cybersourceParams;

    /**
     * Transaction reference number
     */
    private String referenceNumber;
}
