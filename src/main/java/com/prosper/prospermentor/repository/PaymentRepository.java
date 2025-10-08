package com.prosper.prospermentor.repository;

import com.prosper.prospermentor.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Payment entity
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Find payment by checkout request ID
     */
    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);

    /**
     * Find payment by Mpesa receipt number
     */
    Optional<Payment> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    /**
     * Find all payments for a user
     */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find payments by session ID
     */
    List<Payment> findBySessionId(UUID sessionId);

    /**
     * Find payments by subscription ID
     */
    List<Payment> findBySubscriptionId(UUID subscriptionId);

    /**
     * Find payments by status
     */
    List<Payment> findByStatus(Payment.PaymentStatus status);

    /**
     * Find pending payments older than specified time
     */
    @Query("SELECT p FROM Payment p WHERE p.status = com.prosper.prospermentor.entity.Payment$PaymentStatus.PENDING " +
           "AND p.createdAt < :cutoffTime")
    List<Payment> findPendingPaymentsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find failed payments that can be retried
     */
    @Query("SELECT p FROM Payment p WHERE p.status = com.prosper.prospermentor.entity.Payment$PaymentStatus.FAILED " +
           "AND p.retryCount < 3 " +
           "AND p.createdAt > :cutoffTime")
    List<Payment> findRetryableFailedPayments(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find payments by user and status
     */
    List<Payment> findByUserIdAndStatus(UUID userId, Payment.PaymentStatus status);

    /**
     * Find payments by payment type and status
     */
    @Query("SELECT p FROM Payment p WHERE p.paymentType = :paymentType " +
           "AND p.status = :status " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByPaymentTypeAndStatus(
            @Param("paymentType") Payment.PaymentType paymentType,
            @Param("status") Payment.PaymentStatus status
    );

    /**
     * Get total revenue for a date range
     */
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.status = com.prosper.prospermentor.entity.Payment$PaymentStatus.COMPLETED " +
           "AND p.completedAt BETWEEN :start AND :end")
    BigDecimal getTotalRevenueForPeriod(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Count completed payments for a user
     */
    long countByUserIdAndStatus(UUID userId, Payment.PaymentStatus status);

    /**
     * Find recent payments for a user
     */
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findRecentPaymentsByUserId(@Param("userId") UUID userId, Pageable pageable);
}
