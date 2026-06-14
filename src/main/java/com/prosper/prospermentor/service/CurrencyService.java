package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.ExchangeRate;
import com.prosper.prospermentor.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Service for currency conversion operations
 * Handles conversion between different currencies using stored exchange rates
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyService {

    private final ExchangeRateRepository exchangeRateRepository;

    @Value("${app.currency.default:KES}")
    private String defaultCurrency;

    @Value("${app.currency.base:USD}")
    private String baseCurrency;

    /**
     * Convert amount from one currency to another
     *
     * @param amount The amount to convert
     * @param fromCurrency Source currency code (e.g., "USD")
     * @param toCurrency Target currency code (e.g., "KES")
     * @return Converted amount
     */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // If same currency, no conversion needed
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }

        log.debug("Converting {} {} to {}", amount, fromCurrency, toCurrency);

        try {
            // Convert to base currency (USD) first if needed
            BigDecimal amountInBase;
            if (fromCurrency.equalsIgnoreCase(baseCurrency)) {
                amountInBase = amount;
            } else {
                Optional<ExchangeRate> fromRate = exchangeRateRepository.findByCurrencyCode(fromCurrency.toUpperCase());
                if (fromRate.isEmpty() || !fromRate.get().getIsActive()) {
                    throw new IllegalArgumentException("Exchange rate not found for currency: " + fromCurrency);
                }
                // Convert to USD: amount / rate
                amountInBase = amount.divide(fromRate.get().getRate(), 6, RoundingMode.HALF_UP);
            }

            // Convert from base currency to target currency
            BigDecimal result;
            if (toCurrency.equalsIgnoreCase(baseCurrency)) {
                result = amountInBase;
            } else {
                Optional<ExchangeRate> toRate = exchangeRateRepository.findByCurrencyCode(toCurrency.toUpperCase());
                if (toRate.isEmpty() || !toRate.get().getIsActive()) {
                    throw new IllegalArgumentException("Exchange rate not found for currency: " + toCurrency);
                }
                // Convert from USD to target: amount * rate
                result = amountInBase.multiply(toRate.get().getRate());
            }

            // Round to 2 decimal places
            result = result.setScale(2, RoundingMode.HALF_UP);

            log.debug("Converted {} {} to {} {}", amount, fromCurrency, result, toCurrency);
            return result;

        } catch (Exception e) {
            log.error("Failed to convert {} from {} to {}: {}", amount, fromCurrency, toCurrency, e.getMessage());
            throw new RuntimeException("Currency conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert amount from USD to specified currency
     */
    public BigDecimal convertFromUSD(BigDecimal amountInUSD, String toCurrency) {
        return convert(amountInUSD, baseCurrency, toCurrency);
    }

    /**
     * Convert amount from specified currency to USD
     */
    public BigDecimal convertToUSD(BigDecimal amount, String fromCurrency) {
        return convert(amount, fromCurrency, baseCurrency);
    }

    /**
     * Convert amount from USD to default currency (KES)
     */
    public BigDecimal convertFromUSDToDefault(BigDecimal amountInUSD) {
        return convert(amountInUSD, baseCurrency, defaultCurrency);
    }

    /**
     * Convert amount to user's preferred currency, or default (KES) if not specified
     *
     * @param amountInUSD Amount in USD (base currency)
     * @param preferredCurrency User's preferred currency (nullable)
     * @return Converted amount in preferred or default currency
     */
    public BigDecimal convertToUserCurrency(BigDecimal amountInUSD, String preferredCurrency) {
        String targetCurrency = (preferredCurrency != null && !preferredCurrency.trim().isEmpty())
                                ? preferredCurrency
                                : defaultCurrency;

        return convertFromUSD(amountInUSD, targetCurrency);
    }

    /**
     * Get the default currency code
     */
    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    /**
     * Get the base currency code
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Get exchange rate for a specific currency
     */
    public Optional<ExchangeRate> getExchangeRate(String currencyCode) {
        return exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());
    }

    /**
     * Check if a currency is supported
     */
    public boolean isCurrencySupported(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            return false;
        }

        // Base currency is always supported
        if (currencyCode.equalsIgnoreCase(baseCurrency)) {
            return true;
        }

        Optional<ExchangeRate> rate = exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());
        return rate.isPresent() && rate.get().getIsActive();
    }

    /**
     * Format amount with currency symbol
     */
    public String formatAmount(BigDecimal amount, String currencyCode) {
        Optional<ExchangeRate> rate = exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());

        String symbol = currencyCode;
        if (rate.isPresent() && rate.get().getSymbol() != null) {
            symbol = rate.get().getSymbol();
        }

        return String.format("%s %,.2f", symbol, amount);
    }

    /**
     * Get currency symbol for a currency code
     */
    public String getCurrencySymbol(String currencyCode) {
        if (currencyCode.equalsIgnoreCase("USD")) {
            return "$";
        }

        Optional<ExchangeRate> rate = exchangeRateRepository.findByCurrencyCode(currencyCode.toUpperCase());
        return rate.map(ExchangeRate::getSymbol).orElse(currencyCode);
    }
}
