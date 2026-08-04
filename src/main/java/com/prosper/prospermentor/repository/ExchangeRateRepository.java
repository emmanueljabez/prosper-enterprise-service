package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ExchangeRate entity
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /**
     * Find exchange rate by currency code
     */
    Optional<ExchangeRate> findByCurrencyCode(String currencyCode);

    /**
     * Find all active exchange rates
     */
    List<ExchangeRate> findByIsActiveTrue();

    /**
     * Find the default currency
     */
    Optional<ExchangeRate> findByIsDefaultTrue();

    /**
     * Check if currency code exists
     */
    boolean existsByCurrencyCode(String currencyCode);

    /**
     * Find all exchange rates ordered by currency code
     */
    List<ExchangeRate> findAllByOrderByCurrencyCodeAsc();

    /**
     * Find active exchange rates ordered by currency code
     */
    List<ExchangeRate> findByIsActiveTrueOrderByCurrencyCodeAsc();
}
