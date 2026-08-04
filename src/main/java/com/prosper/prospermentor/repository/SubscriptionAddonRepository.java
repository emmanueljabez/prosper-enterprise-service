package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.SubscriptionAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for SubscriptionAddon entity
 */
@Repository
public interface SubscriptionAddonRepository extends JpaRepository<SubscriptionAddon, UUID> {

    /**
     * Find all addons for a subscription
     */
    List<SubscriptionAddon> findBySubscriptionIdOrderByPurchasedAtDesc(UUID subscriptionId);

    /**
     * Find active addons for a subscription
     */
    List<SubscriptionAddon> findBySubscriptionIdAndStatus(UUID subscriptionId, SubscriptionAddon.AddonStatus status);

    /**
     * Find addons by subscription and type
     */
    List<SubscriptionAddon> findBySubscriptionIdAndAddonType(UUID subscriptionId, String addonType);

    /**
     * Find active addons with remaining units
     */
    @Query("SELECT a FROM SubscriptionAddon a WHERE a.subscription.id = :subscriptionId " +
           "AND a.status = 'ACTIVE' AND a.used < a.quantity " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<SubscriptionAddon> findActiveAddonsWithRemaining(@Param("subscriptionId") UUID subscriptionId, @Param("now") LocalDateTime now);

    /**
     * Find consumed session add-ons that can be restored after a mentor decline.
     */
    @Query("SELECT a FROM SubscriptionAddon a WHERE a.subscription.id = :subscriptionId " +
           "AND a.addonType = 'EXTRA_SESSION' AND a.used > 0 " +
           "AND a.status IN ('ACTIVE', 'EXHAUSTED') " +
           "ORDER BY a.updatedAt DESC, a.purchasedAt DESC")
    List<SubscriptionAddon> findRestorableSessionAddons(@Param("subscriptionId") UUID subscriptionId);

    /**
     * Find expired addons that need status update
     */
    @Query("SELECT a FROM SubscriptionAddon a WHERE a.status = 'ACTIVE' " +
           "AND a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<SubscriptionAddon> findExpiredAddons(@Param("now") LocalDateTime now);

    /**
     * Get total remaining units for a specific addon type
     */
    @Query("SELECT COALESCE(SUM(a.quantity - a.used), 0) FROM SubscriptionAddon a " +
           "WHERE a.subscription.id = :subscriptionId AND a.addonType = :addonType " +
           "AND a.status = 'ACTIVE' AND a.used < a.quantity " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    Integer getTotalRemainingUnits(@Param("subscriptionId") UUID subscriptionId,
                                   @Param("addonType") String addonType,
                                   @Param("now") LocalDateTime now);

    /**
     * Find addons by payment ID
     */
    List<SubscriptionAddon> findByPaymentId(UUID paymentId);
}
