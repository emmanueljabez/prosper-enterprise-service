package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.ExchangeRate;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing exchange rates
 * Handles CRUD operations and updating rates from external sources
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.currency.api.enabled:false}")
    private boolean currencyApiEnabled;

    @Value("${app.currency.api.url:https://api.exchangerate-api.com/v4/latest/USD}")
    private String currencyApiUrl;

    @Value("${app.currency.default:KES}")
    private String defaultCurrency;

    /**
     * Get all exchange rates
     */
    @Transactional(readOnly = true)
    public List<ExchangeRate> getAllRates() {
        return exchangeRateRepository.findAllByOrderByCurrencyCodeAsc();
    }

    /**
     * Get all active exchange rates
     */
    @Transactional(readOnly = true)
    public List<ExchangeRate> getActiveRates() {
        return exchangeRateRepository.findByIsActiveTrueOrderByCurrencyCodeAsc();
    }

    /**
     * Get exchange rate by currency code
     */
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> getRateByCurrency(String currencyCode) {
        return exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());
    }

    /**
     * Get default currency exchange rate
     */
    @Transactional(readOnly = true)
    public Optional<ExchangeRate> getDefaultCurrencyRate() {
        return exchangeRateRepository.findByIsDefaultTrue();
    }

    /**
     * Create or update exchange rate
     */
    public ApiResponse<ExchangeRate> createOrUpdateRate(String currencyCode, String currencyName,
                                                         BigDecimal rate, String symbol, String source) {
        log.info("Creating/updating exchange rate for {}: {}", currencyCode, rate);

        if (currencyCode == null || currencyCode.trim().length() != 3) {
            return ApiResponse.error("Invalid currency code. Must be a 3-letter ISO code.");
        }

        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("Exchange rate must be greater than zero.");
        }

        Optional<ExchangeRate> existingRate = exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());

        ExchangeRate exchangeRate;
        if (existingRate.isPresent()) {
            exchangeRate = existingRate.get();
            exchangeRate.updateRate(rate, source != null ? source : "MANUAL");
            if (currencyName != null) {
                exchangeRate.setCurrencyName(currencyName);
            }
            if (symbol != null) {
                exchangeRate.setSymbol(symbol);
            }
            log.info("Updated exchange rate for {}: {}", currencyCode, rate);
        } else {
            exchangeRate = new ExchangeRate();
            exchangeRate.setCurrencyCode(currencyCode.toUpperCase());
            exchangeRate.setCurrencyName(currencyName != null ? currencyName : currencyCode);
            exchangeRate.setRate(rate);
            exchangeRate.setSymbol(symbol);
            exchangeRate.setSource(source != null ? source : "MANUAL");
            exchangeRate.setIsActive(true);
            exchangeRate.setIsDefault(currencyCode.equalsIgnoreCase(defaultCurrency));
            exchangeRate.setLastUpdated(LocalDateTime.now());
            log.info("Created new exchange rate for {}: {}", currencyCode, rate);
        }

        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        return ApiResponse.success("Exchange rate saved successfully", savedRate);
    }

    /**
     * Update exchange rate
     */
    public ApiResponse<ExchangeRate> updateRate(UUID id, BigDecimal newRate, String source) {
        log.info("Updating exchange rate {}: {}", id, newRate);

        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isEmpty()) {
            return ApiResponse.error("Exchange rate not found");
        }

        if (newRate == null || newRate.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("Exchange rate must be greater than zero");
        }

        ExchangeRate exchangeRate = rateOpt.get();
        exchangeRate.updateRate(newRate, source != null ? source : "MANUAL");

        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Updated exchange rate for {}: {}", savedRate.getCurrencyCode(), newRate);

        return ApiResponse.success("Exchange rate updated successfully", savedRate);
    }

    /**
     * Toggle currency active status
     */
    public ApiResponse<ExchangeRate> toggleActiveStatus(UUID id) {
        log.info("Toggling active status for exchange rate: {}", id);

        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isEmpty()) {
            return ApiResponse.error("Exchange rate not found");
        }

        ExchangeRate exchangeRate = rateOpt.get();

        // Don't allow deactivating the default currency
        if (exchangeRate.getIsDefault() && exchangeRate.getIsActive()) {
            return ApiResponse.error("Cannot deactivate the default currency");
        }

        if (exchangeRate.getIsActive()) {
            exchangeRate.deactivate();
        } else {
            exchangeRate.activate();
        }

        ExchangeRate savedRate = exchangeRateRepository.save(exchangeRate);
        log.info("Toggled {} to {}", savedRate.getCurrencyCode(),
                savedRate.getIsActive() ? "active" : "inactive");

        return ApiResponse.success("Exchange rate status updated", savedRate);
    }

    /**
     * Set a currency as default
     */
    public ApiResponse<ExchangeRate> setAsDefault(UUID id) {
        log.info("Setting exchange rate {} as default", id);

        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isEmpty()) {
            return ApiResponse.error("Exchange rate not found");
        }

        ExchangeRate newDefault = rateOpt.get();

        // Remove default status from current default
        Optional<ExchangeRate> currentDefault = exchangeRateRepository.findByIsDefaultTrue();
        if (currentDefault.isPresent() && !currentDefault.get().getId().equals(id)) {
            ExchangeRate oldDefault = currentDefault.get();
            oldDefault.removeDefault();
            exchangeRateRepository.save(oldDefault);
            log.info("Removed default status from {}", oldDefault.getCurrencyCode());
        }

        // Set new default
        newDefault.setAsDefault();
        newDefault.activate(); // Ensure it's active

        ExchangeRate savedRate = exchangeRateRepository.save(newDefault);
        log.info("Set {} as default currency", savedRate.getCurrencyCode());

        return ApiResponse.success("Default currency updated", savedRate);
    }

    /**
     * Delete exchange rate
     */
    public ApiResponse<Void> deleteRate(UUID id) {
        log.info("Deleting exchange rate: {}", id);

        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findById(id);
        if (rateOpt.isEmpty()) {
            return ApiResponse.error("Exchange rate not found");
        }

        ExchangeRate exchangeRate = rateOpt.get();

        // Don't allow deleting the default currency
        if (exchangeRate.getIsDefault()) {
            return ApiResponse.error("Cannot delete the default currency");
        }

        exchangeRateRepository.delete(exchangeRate);
        log.info("Deleted exchange rate for {}", exchangeRate.getCurrencyCode());

        return ApiResponse.success("Exchange rate deleted successfully");
    }

    /**
     * Refresh exchange rates from external API
     * This should be called periodically (e.g., daily) via a scheduled job
     */
    public ApiResponse<Map<String, Object>> refreshRatesFromAPI() {
        if (!currencyApiEnabled) {
            log.warn("Currency API is not enabled. Cannot refresh rates.");
            return ApiResponse.error("Currency API is not enabled");
        }

        log.info("Refreshing exchange rates from external API: {}", currencyApiUrl);

        try {
            // Fetch rates from external API
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(currencyApiUrl, Map.class);

            if (response == null || !response.containsKey("rates")) {
                return ApiResponse.error("Invalid response from currency API");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rates = (Map<String, Object>) response.get("rates");

            int updatedCount = 0;
            int createdCount = 0;

            // Update rates for currencies we have in our database
            for (Map.Entry<String, Object> entry : rates.entrySet()) {
                String currencyCode = entry.getKey();
                BigDecimal rate = new BigDecimal(entry.getValue().toString());

                Optional<ExchangeRate> existingRate = exchangeRateRepository.findByCurrencyCode(currencyCode);

                if (existingRate.isPresent()) {
                    // Update existing rate
                    ExchangeRate exchangeRate = existingRate.get();
                    exchangeRate.updateRate(rate, "API");
                    exchangeRateRepository.save(exchangeRate);
                    updatedCount++;
                } else {
                    // Only create for important currencies (add more as needed)
                    if (isImportantCurrency(currencyCode)) {
                        ExchangeRate newRate = new ExchangeRate();
                        newRate.setCurrencyCode(currencyCode);
                        newRate.setCurrencyName(getCurrencyName(currencyCode));
                        newRate.setRate(rate);
                        newRate.setSymbol(getCurrencySymbol(currencyCode));
                        newRate.setSource("API");
                        newRate.setIsActive(true);
                        newRate.setIsDefault(false);
                        newRate.setLastUpdated(LocalDateTime.now());
                        exchangeRateRepository.save(newRate);
                        createdCount++;
                    }
                }
            }

            log.info("Refreshed exchange rates from API. Updated: {}, Created: {}", updatedCount, createdCount);

            return ApiResponse.success("Exchange rates refreshed successfully",
                    Map.of("updated", updatedCount, "created", createdCount));

        } catch (Exception e) {
            log.error("Failed to refresh exchange rates from API: {}", e.getMessage(), e);
            return ApiResponse.error("Failed to refresh exchange rates: " + e.getMessage());
        }
    }

    /**
     * Initialize default exchange rates (called on startup if needed)
     */
    public void initializeDefaultRates() {
        log.info("Checking if default exchange rates need initialization");

        // Check if we have any rates
        if (exchangeRateRepository.count() > 0) {
            log.info("Exchange rates already initialized");
            return;
        }

        log.info("Initializing default exchange rates");

        // Create default rates for East African currencies
        createOrUpdateRate("KES", "Kenyan Shilling", new BigDecimal("130.00"), "KSh", "MANUAL");
        createOrUpdateRate("UGX", "Ugandan Shilling", new BigDecimal("3700.00"), "UGX", "MANUAL");
        createOrUpdateRate("TZS", "Tanzanian Shilling", new BigDecimal("2350.00"), "TSh", "MANUAL");
        createOrUpdateRate("RWF", "Rwandan Franc", new BigDecimal("1050.00"), "RWF", "MANUAL");
        createOrUpdateRate("NGN", "Nigerian Naira", new BigDecimal("750.00"), "₦", "MANUAL");
        createOrUpdateRate("ZAR", "South African Rand", new BigDecimal("18.50"), "R", "MANUAL");
        createOrUpdateRate("GHS", "Ghanaian Cedi", new BigDecimal("12.00"), "GH₵", "MANUAL");

        // Set KES as default
        Optional<ExchangeRate> kesRate = exchangeRateRepository.findByCurrencyCode("KES");
        kesRate.ifPresent(rate -> {
            rate.setAsDefault();
            exchangeRateRepository.save(rate);
        });

        log.info("Default exchange rates initialized successfully");
    }

    /**
     * Helper method to determine if a currency is important enough to auto-create
     */
    private boolean isImportantCurrency(String currencyCode) {
        return List.of("KES", "UGX", "TZS", "RWF", "NGN", "ZAR", "GHS", "EUR", "GBP")
                .contains(currencyCode);
    }

    /**
     * Helper method to get currency name
     */
    private String getCurrencyName(String currencyCode) {
        return switch (currencyCode) {
            case "KES" -> "Kenyan Shilling";
            case "UGX" -> "Ugandan Shilling";
            case "TZS" -> "Tanzanian Shilling";
            case "RWF" -> "Rwandan Franc";
            case "NGN" -> "Nigerian Naira";
            case "ZAR" -> "South African Rand";
            case "GHS" -> "Ghanaian Cedi";
            case "EUR" -> "Euro";
            case "GBP" -> "British Pound";
            default -> currencyCode;
        };
    }

    /**
     * Helper method to get currency symbol
     */
    private String getCurrencySymbol(String currencyCode) {
        return switch (currencyCode) {
            case "KES" -> "KSh";
            case "UGX" -> "UGX";
            case "TZS" -> "TSh";
            case "RWF" -> "RWF";
            case "NGN" -> "₦";
            case "ZAR" -> "R";
            case "GHS" -> "GH₵";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "USD" -> "$";
            default -> currencyCode;
        };
    }
}
