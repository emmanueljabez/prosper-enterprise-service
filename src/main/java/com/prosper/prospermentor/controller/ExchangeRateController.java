package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.ExchangeRate;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing exchange rates
 * Provides endpoints for viewing and managing currency exchange rates
 */
@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    /**
     * Get all exchange rates (Admin only)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExchangeRate>>> getAllRates() {
        log.info("Fetching all exchange rates");
        List<ExchangeRate> rates = exchangeRateService.getAllRates();
        return ResponseEntity.ok(ApiResponse.success("Exchange rates retrieved successfully", rates));
    }

    /**
     * Get all active exchange rates (Public)
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ExchangeRate>>> getActiveRates() {
        log.info("Fetching active exchange rates");
        List<ExchangeRate> rates = exchangeRateService.getActiveRates();
        return ResponseEntity.ok(ApiResponse.success("Active exchange rates retrieved successfully", rates));
    }

    /**
     * Get exchange rate by currency code (Public)
     */
    @GetMapping("/{currencyCode}")
    public ResponseEntity<ApiResponse<ExchangeRate>> getRateByCurrency(@PathVariable String currencyCode) {
        log.info("Fetching exchange rate for currency: {}", currencyCode);
        return exchangeRateService.getRateByCurrency(currencyCode)
                .map(rate -> ResponseEntity.ok(ApiResponse.success("Exchange rate found", rate)))
                .orElse(ResponseEntity.ok(ApiResponse.error("Exchange rate not found for currency: " + currencyCode)));
    }

    /**
     * Get default currency exchange rate (Public)
     */
    @GetMapping("/default")
    public ResponseEntity<ApiResponse<ExchangeRate>> getDefaultCurrencyRate() {
        log.info("Fetching default currency exchange rate");
        return exchangeRateService.getDefaultCurrencyRate()
                .map(rate -> ResponseEntity.ok(ApiResponse.success("Default currency rate found", rate)))
                .orElse(ResponseEntity.ok(ApiResponse.error("Default currency rate not found")));
    }

    /**
     * Create or update exchange rate (Admin only)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExchangeRate>> createOrUpdateRate(
            @RequestBody CreateExchangeRateRequest request) {
        log.info("Creating/updating exchange rate for {}", request.getCurrencyCode());
        ApiResponse<ExchangeRate> response = exchangeRateService.createOrUpdateRate(
                request.getCurrencyCode(),
                request.getCurrencyName(),
                request.getRate(),
                request.getSymbol(),
                "MANUAL"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Update exchange rate (Admin only)
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExchangeRate>> updateRate(
            @PathVariable UUID id,
            @RequestBody UpdateExchangeRateRequest request) {
        log.info("Updating exchange rate {}", id);
        ApiResponse<ExchangeRate> response = exchangeRateService.updateRate(
                id,
                request.getRate(),
                "MANUAL"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Toggle active status (Admin only)
     */
    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<ExchangeRate>> toggleActiveStatus(@PathVariable UUID id) {
        log.info("Toggling active status for exchange rate {}", id);
        ApiResponse<ExchangeRate> response = exchangeRateService.toggleActiveStatus(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Set as default currency (Admin only)
     */
    @PutMapping("/{id}/set-default")
    public ResponseEntity<ApiResponse<ExchangeRate>> setAsDefault(@PathVariable UUID id) {
        log.info("Setting exchange rate {} as default", id);
        ApiResponse<ExchangeRate> response = exchangeRateService.setAsDefault(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete exchange rate (Admin only)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRate(@PathVariable UUID id) {
        log.info("Deleting exchange rate {}", id);
        ApiResponse<Void> response = exchangeRateService.deleteRate(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh rates from external API (Admin only)
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshRatesFromAPI() {
        log.info("Refreshing exchange rates from external API");
        ApiResponse<Map<String, Object>> response = exchangeRateService.refreshRatesFromAPI();
        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for creating/updating exchange rate
     */
    @lombok.Data
    public static class CreateExchangeRateRequest {
        private String currencyCode;
        private String currencyName;
        private BigDecimal rate;
        private String symbol;
    }

    /**
     * Request DTO for updating exchange rate
     */
    @lombok.Data
    public static class UpdateExchangeRateRequest {
        private BigDecimal rate;
    }
}
