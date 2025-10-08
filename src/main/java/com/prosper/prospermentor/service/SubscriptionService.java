package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user subscriptions
 */
@Service
@Slf4j
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final MpesaService mpesaService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionPlanRepository subscriptionPlanRepository,
                               MpesaService mpesaService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.mpesaService = mpesaService;
    }

    /**
     * Get active subscription for a user
     */
    @Transactional(readOnly = true)
    public Optional<Subscription> getActiveSubscription(UUID userId) {
        return subscriptionRepository.findActiveSubscriptionByUserId(userId, LocalDateTime.now());
    }

    /**
     * Check if user can book a session (has available sessions in subscription)
     */
    @Transactional(readOnly = true)
    public boolean canBookSession(UUID userId) {
        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);

        if (subscriptionOpt.isEmpty()) {
            log.info("User {} has no active subscription", userId);
            return false;
        }

        Subscription subscription = subscriptionOpt.get();

        // Check if subscription allows unlimited sessions
        if (subscription.getPlan() != null && subscription.getPlan().isUnlimited()) {
            return true;
        }

        // Check if user has available sessions
        boolean hasAvailable = subscription.hasAvailableSessions();

        if (!hasAvailable) {
            log.info("User {} has exhausted their subscription sessions ({}/{})",
                    userId, subscription.getSessionsUsed(), subscription.getSessionsPerMonth());
        }

        return hasAvailable;
    }

    /**
     * Check if user has maxed out their subscription
     */
    @Transactional(readOnly = true)
    public boolean hasMaxedOutSubscription(UUID userId) {
        return !canBookSession(userId);
    }

    /**
     * Consume a session from user's subscription
     */
    public void consumeSession(UUID userId) {
        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);

        if (subscriptionOpt.isEmpty()) {
            throw new IllegalStateException("No active subscription found for user: " + userId);
        }

        Subscription subscription = subscriptionOpt.get();

        // Don't consume sessions for unlimited plans
        if (subscription.getPlan() != null && subscription.getPlan().isUnlimited()) {
            log.info("User {} has unlimited plan, not consuming session", userId);
            return;
        }

        if (!subscription.hasAvailableSessions()) {
            throw new IllegalStateException("No available sessions in subscription for user: " + userId);
        }

        subscription.incrementSessionsUsed();
        subscriptionRepository.save(subscription);

        log.info("Consumed session for user {}. Sessions used: {}/{}",
                userId, subscription.getSessionsUsed(), subscription.getSessionsPerMonth());
    }

    /**
     * Create a new subscription for a user
     * Hybrid approach: Free plans activated immediately, paid plans require payment
     */
    public ApiResponse<SubscriptionCreationResponse> createSubscription(CreateSubscriptionRequest request) {
        log.info("Creating subscription for user {} with plan {}", request.getUserId(), request.getPlanId());

        // Check if user already has an active subscription
        Optional<Subscription> existingSubscription = getActiveSubscription(request.getUserId());
        if (existingSubscription.isPresent()) {
            return ApiResponse.error("User already has an active subscription");
        }

        // Get subscription plan
        Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(request.getPlanId());
        if (planOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan plan = planOpt.get();
        if (!plan.getIsActive()) {
            return ApiResponse.error("Subscription plan is not active");
        }

        // Check if plan is free or trial
        boolean isFreeSubscription = plan.isFree() || request.isTrial();

        if (isFreeSubscription) {
            // FREE FLOW: Create and activate subscription immediately
            Subscription subscription = createActiveSubscription(request, plan);
            SubscriptionCreationResponse response = SubscriptionCreationResponse.forFreeSubscription(subscription);
            return ApiResponse.success("Subscription created successfully", response);
        } else {
            // PAID FLOW: Create pending subscription and initiate payment

            // Validate phone number is provided
            if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
                return ApiResponse.error("Phone number is required for paid subscriptions");
            }

            // Create subscription with PENDING_PAYMENT status
            Subscription subscription = createPendingSubscription(request, plan);

            try {
                // Initiate payment via Mpesa
                Payment payment = mpesaService.initiateSTKPush(
                    request.getUserId(),
                    null, // sessionId - not applicable
                    subscription.getId(), // subscriptionId
                    Payment.PaymentType.SUBSCRIPTION,
                    plan.getCost(),
                    request.getPhoneNumber(),
                    "Subscription payment for " + plan.getName()
                );

                log.info("Payment initiated for subscription {} with payment ID {}",
                    subscription.getId(), payment.getId());

                SubscriptionCreationResponse response = SubscriptionCreationResponse.forPaidSubscription(subscription, payment);
                return ApiResponse.success("Payment initiated. Please complete payment on your phone.", response);

            } catch (Exception e) {
                log.error("Failed to initiate payment for subscription {}: {}",
                    subscription.getId(), e.getMessage(), e);

                // Cancel the pending subscription since payment failed
                subscription.cancel();
                subscriptionRepository.save(subscription);

                return ApiResponse.error("Failed to initiate payment: " + e.getMessage());
            }
        }
    }

    /**
     * Create an active subscription (for free plans or trials)
     */
    private Subscription createActiveSubscription(CreateSubscriptionRequest request, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(plan.getDurationMonths());

        Subscription subscription = new Subscription();
        subscription.setUserId(request.getUserId());
        subscription.setPlan(plan);
        subscription.setSessionsPerMonth(plan.getSessionsPerPeriod());
        subscription.setSessionsUsed(0);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(endDate);
        subscription.setStatus(request.isTrial() ? Subscription.SubscriptionStatus.TRIAL : Subscription.SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(true);
        subscription.setIsTrial(request.isTrial());

        subscription = subscriptionRepository.save(subscription);
        log.info("Created active subscription {} for user {}", subscription.getId(), request.getUserId());

        return subscription;
    }

    /**
     * Create a pending subscription (for paid plans awaiting payment)
     */
    private Subscription createPendingSubscription(CreateSubscriptionRequest request, SubscriptionPlan plan) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(plan.getDurationMonths());

        Subscription subscription = new Subscription();
        subscription.setUserId(request.getUserId());
        subscription.setPlan(plan);
        subscription.setSessionsPerMonth(plan.getSessionsPerPeriod());
        subscription.setSessionsUsed(0);
        subscription.setStartDate(now); // Will be updated on payment confirmation
        subscription.setEndDate(endDate); // Will be updated on payment confirmation
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(endDate);
        subscription.setStatus(Subscription.SubscriptionStatus.PENDING_PAYMENT);
        subscription.setAutoRenew(true);
        subscription.setIsTrial(false);

        subscription = subscriptionRepository.save(subscription);
        log.info("Created pending subscription {} for user {}", subscription.getId(), request.getUserId());

        return subscription;
    }

    /**
     * Activate a pending subscription after successful payment
     */
    public ApiResponse<Subscription> activateSubscription(UUID subscriptionId) {
        log.info("Activating subscription {}", subscriptionId);

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("Subscription not found");
        }

        Subscription subscription = subscriptionOpt.get();

        if (subscription.getStatus() != Subscription.SubscriptionStatus.PENDING_PAYMENT) {
            return ApiResponse.error("Subscription is not in pending payment status");
        }

        // Update subscription to active
        LocalDateTime now = LocalDateTime.now();
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(subscription.getPlan().getDurationMonths()));
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(subscription.getPlan().getDurationMonths()));

        subscription = subscriptionRepository.save(subscription);

        log.info("Activated subscription {}", subscriptionId);

        return ApiResponse.success("Subscription activated successfully", subscription);
    }

    /**
     * Cancel a subscription due to payment failure
     */
    public void cancelSubscriptionByPaymentFailure(UUID subscriptionId) {
        log.info("Cancelling subscription {} due to payment failure", subscriptionId);

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isEmpty()) {
            log.warn("Subscription {} not found for cancellation", subscriptionId);
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        if (subscription.getStatus() == Subscription.SubscriptionStatus.PENDING_PAYMENT) {
            subscription.cancel();
            subscriptionRepository.save(subscription);
            log.info("Cancelled pending subscription {} due to payment failure", subscriptionId);
        }
    }

    /**
     * Cleanup expired pending subscriptions (scheduled job)
     * Cancels subscriptions that have been in PENDING_PAYMENT for more than 30 minutes
     */
    public void cleanupPendingSubscriptions() {
        log.info("Cleaning up expired pending subscriptions");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);

        List<Subscription> pendingSubscriptions = subscriptionRepository.findAll().stream()
            .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.PENDING_PAYMENT)
            .filter(s -> s.getCreatedAt().isBefore(cutoffTime))
            .toList();

        for (Subscription subscription : pendingSubscriptions) {
            subscription.cancel();
            subscriptionRepository.save(subscription);
            log.info("Cleaned up expired pending subscription {}", subscription.getId());
        }

        log.info("Cleaned up {} pending subscriptions", pendingSubscriptions.size());
    }

    /**
     * Upgrade user's subscription
     */
    public ApiResponse<Subscription> upgradeSubscription(UpgradeSubscriptionRequest request) {
        log.info("Upgrading subscription for user {} to plan {}", request.getUserId(), request.getNewPlanId());

        Optional<Subscription> subscriptionOpt = getActiveSubscription(request.getUserId());
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("No active subscription found for user");
        }

        // Get new subscription plan
        Optional<SubscriptionPlan> newPlanOpt = subscriptionPlanRepository.findById(request.getNewPlanId());
        if (newPlanOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan newPlan = newPlanOpt.get();
        if (!newPlan.getIsActive()) {
            return ApiResponse.error("Subscription plan is not active");
        }

        Subscription subscription = subscriptionOpt.get();

        // Check if new plan is actually an upgrade (higher cost)
        if (subscription.getPlan() != null &&
            newPlan.getCost().compareTo(subscription.getPlan().getCost()) <= 0) {
            return ApiResponse.error("Cannot upgrade to same or lower plan");
        }

        subscription.setPlan(newPlan);
        subscription.setSessionsPerMonth(newPlan.getSessionsPerPeriod());
        // Reset sessions used when upgrading
        subscription.setSessionsUsed(0);
        subscription.setIsTrial(false);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);

        subscription = subscriptionRepository.save(subscription);

        log.info("Upgraded subscription {} for user {} to plan {}",
                subscription.getId(), request.getUserId(), newPlan.getName());

        return ApiResponse.success("Subscription upgraded successfully", subscription);
    }

    /**
     * Cancel user's subscription
     */
    public ApiResponse<Void> cancelSubscription(CancelSubscriptionRequest request) {
        log.info("Cancelling subscription for user {}", request.getUserId());

        Optional<Subscription> subscriptionOpt = getActiveSubscription(request.getUserId());
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("No active subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.cancel();
        subscriptionRepository.save(subscription);

        log.info("Cancelled subscription {} for user {}", subscription.getId(), request.getUserId());

        return ApiResponse.success("Subscription cancelled successfully");
    }

    /**
     * Renew subscription
     */
    public Subscription renewSubscription(UUID subscriptionId) {
        log.info("Renewing subscription {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));

        subscription.renew();
        subscription = subscriptionRepository.save(subscription);

        log.info("Renewed subscription {}", subscriptionId);

        return subscription;
    }

    /**
     * Get subscription by ID
     */
    @Transactional(readOnly = true)
    public Subscription getSubscriptionById(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
    }

    /**
     * Get all subscriptions for a user
     */
    @Transactional(readOnly = true)
    public List<Subscription> getUserSubscriptions(UUID userId) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Process expired subscriptions (should be run by scheduled job)
     */
    public void processExpiredSubscriptions() {
        log.info("Processing expired subscriptions");

        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findExpiredActiveSubscriptions(LocalDateTime.now());

        for (Subscription subscription : expiredSubscriptions) {
            if (subscription.getAutoRenew()) {
                try {
                    renewSubscription(subscription.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-renew subscription {}: {}",
                            subscription.getId(), e.getMessage());
                    subscription.setStatus(Subscription.SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                }
            } else {
                subscription.setStatus(Subscription.SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
            }
        }

        log.info("Processed {} expired subscriptions", expiredSubscriptions.size());
    }

    /**
     * Reset sessions for new billing period
     */
    public void resetBillingPeriod(UUID subscriptionId) {
        log.info("Resetting billing period for subscription {}", subscriptionId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));

        subscription.resetSessionsUsed();
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));

        subscriptionRepository.save(subscription);

        log.info("Reset billing period for subscription {}", subscriptionId);
    }

    /**
     * Get remaining sessions count for a user
     */
    @Transactional(readOnly = true)
    public int getRemainingSessionsCount(UUID userId) {
        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);

        if (subscriptionOpt.isEmpty()) {
            return 0;
        }

        Subscription subscription = subscriptionOpt.get();

        // Unlimited plans
        if (subscription.getPlan() != null && subscription.getPlan().isUnlimited()) {
            return Integer.MAX_VALUE;
        }

        return subscription.getRemainingSessionsCount();
    }

    /**
     * Get all subscription plans
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findAllByOrderByDisplayOrderAsc();
    }

    /**
     * Get all active subscription plans
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Get subscription plan by ID
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> getPlanById(UUID planId) {
        return subscriptionPlanRepository.findById(planId);
    }

    /**
     * Get subscription plan by code
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionPlan> getPlanByCode(String code) {
        return subscriptionPlanRepository.findByCode(code);
    }

    /**
     * Create a new subscription plan
     */
    public ApiResponse<SubscriptionPlan> createSubscriptionPlan(CreateSubscriptionPlanRequest request) {
        log.info("Creating subscription plan: {}", request.getName());

        // Check if plan code already exists
        if (subscriptionPlanRepository.existsByCode(request.getCode())) {
            return ApiResponse.error("Subscription plan with code '" + request.getCode() + "' already exists");
        }

        // Check if plan name already exists
        if (subscriptionPlanRepository.findByName(request.getName()).isPresent()) {
            return ApiResponse.error("Subscription plan with name '" + request.getName() + "' already exists");
        }

        // Convert DTO to entity
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(request.getName());
        plan.setCode(request.getCode());
        plan.setDescription(request.getDescription());
        plan.setCost(request.getCost());
        plan.setCurrency(request.getCurrency());
        plan.setSessionsPerPeriod(request.getSessionsPerPeriod());
        plan.setDurationMonths(request.getDurationMonths());
        plan.setIsActive(request.getIsActive());
        plan.setDisplayOrder(request.getDisplayOrder());
        plan.setFeatures(request.getFeatures());

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);

        log.info("Created subscription plan {} with ID {}", savedPlan.getName(), savedPlan.getId());

        return ApiResponse.success("Subscription plan created successfully", savedPlan);
    }

    /**
     * Update an existing subscription plan
     */
    public ApiResponse<SubscriptionPlan> updateSubscriptionPlan(UUID planId, UpdateSubscriptionPlanRequest request) {
        log.info("Updating subscription plan: {}", planId);

        Optional<SubscriptionPlan> existingPlanOpt = subscriptionPlanRepository.findById(planId);
        if (existingPlanOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan existingPlan = existingPlanOpt.get();

        // Update only non-null fields from request
        if (request.getName() != null) {
            // Check if new name conflicts with another plan
            Optional<SubscriptionPlan> planWithSameName = subscriptionPlanRepository.findByName(request.getName());
            if (planWithSameName.isPresent() && !planWithSameName.get().getId().equals(planId)) {
                return ApiResponse.error("Plan name already exists");
            }
            existingPlan.setName(request.getName());
        }

        if (request.getDescription() != null) {
            existingPlan.setDescription(request.getDescription());
        }

        if (request.getCost() != null) {
            existingPlan.setCost(request.getCost());
        }

        if (request.getCurrency() != null) {
            existingPlan.setCurrency(request.getCurrency());
        }

        if (request.getSessionsPerPeriod() != null) {
            existingPlan.setSessionsPerPeriod(request.getSessionsPerPeriod());
        }

        if (request.getDurationMonths() != null) {
            existingPlan.setDurationMonths(request.getDurationMonths());
        }

        if (request.getIsActive() != null) {
            existingPlan.setIsActive(request.getIsActive());
        }

        if (request.getDisplayOrder() != null) {
            existingPlan.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getFeatures() != null) {
            existingPlan.setFeatures(request.getFeatures());
        }

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(existingPlan);

        log.info("Updated subscription plan {}", savedPlan.getId());

        return ApiResponse.success("Subscription plan updated successfully", savedPlan);
    }

    /**
     * Delete a subscription plan (soft delete by marking as inactive)
     */
    public ApiResponse<Void> deleteSubscriptionPlan(UUID planId) {
        log.info("Deleting subscription plan: {}", planId);

        Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan plan = planOpt.get();

        // Check if any active subscriptions are using this plan
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findActiveSubscriptionByUserId(plan.getId(), LocalDateTime.now())
                .stream().toList();

        if (!activeSubscriptions.isEmpty()) {
            return ApiResponse.error("Cannot delete plan with active subscriptions. Deactivate the plan instead.");
        }

        // Soft delete by marking as inactive
        plan.setIsActive(false);
        subscriptionPlanRepository.save(plan);

        log.info("Deactivated subscription plan {}", planId);

        return ApiResponse.success("Subscription plan deactivated successfully");
    }

    /**
     * Toggle subscription plan active status
     */
    public ApiResponse<SubscriptionPlan> togglePlanActiveStatus(UUID planId) {
        log.info("Toggling active status for subscription plan: {}", planId);

        Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan plan = planOpt.get();
        plan.setIsActive(!plan.getIsActive());

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);

        log.info("Toggled subscription plan {} to {}", planId, savedPlan.getIsActive() ? "active" : "inactive");

        return ApiResponse.success("Subscription plan status updated successfully", savedPlan);
    }
}
