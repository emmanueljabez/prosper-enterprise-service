package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Subscription entity
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Find active subscription for a user
     */
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId " +
           "AND s.status = 'ACTIVE' " +
           "AND s.startDate <= :now " +
           "AND s.endDate > :now " +
           "ORDER BY s.createdAt DESC")
    Optional<Subscription> findActiveSubscriptionByUserId(
            @Param("userId") UUID userId,
            @Param("now") LocalDateTime now
    );

    /**
     * Find all subscriptions for a user
     */
    List<Subscription> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find subscriptions by status
     */
    List<Subscription> findByStatus(Subscription.SubscriptionStatus status);

    /**
     * Find subscriptions expiring soon
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' " +
           "AND s.endDate BETWEEN :start AND :end " +
           "AND s.autoRenew = true")
    List<Subscription> findSubscriptionsExpiringBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Find expired subscriptions that need status update
     */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' " +
           "AND s.endDate < :now")
    List<Subscription> findExpiredActiveSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Check if user has any active subscription
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
           "FROM Subscription s WHERE s.userId = :userId " +
           "AND s.status = 'ACTIVE' " +
           "AND s.startDate <= :now " +
           "AND s.endDate > :now")
    boolean hasActiveSubscription(
            @Param("userId") UUID userId,
            @Param("now") LocalDateTime now
    );

    /**
     * Find trial subscriptions
     */
    @Query("SELECT s FROM Subscription s WHERE s.isTrial = true " +
           "AND s.status = 'ACTIVE'")
    List<Subscription> findActiveTrialSubscriptions();

    /**
     * Count active subscriptions by plan type
     */
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.plan = :planType " +
           "AND s.status = 'ACTIVE'")
    long countActiveSubscriptionsByPlan(
            @Param("planType") SubscriptionPlan planType
    );
}