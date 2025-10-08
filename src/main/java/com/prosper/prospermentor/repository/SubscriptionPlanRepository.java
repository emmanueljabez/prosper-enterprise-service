package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SubscriptionPlan entity
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    /**
     * Find subscription plan by code
     */
    Optional<SubscriptionPlan> findByCode(String code);

    /**
     * Find subscription plan by name
     */
    Optional<SubscriptionPlan> findByName(String name);

    /**
     * Find all active subscription plans
     */
    List<SubscriptionPlan> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find all subscription plans ordered by display order
     */
    List<SubscriptionPlan> findAllByOrderByDisplayOrderAsc();

    /**
     * Check if a plan code exists
     */
    boolean existsByCode(String code);
}
