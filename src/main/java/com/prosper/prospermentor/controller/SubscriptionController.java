package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing user subscriptions
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Subscription management APIs")
@Slf4j
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Get user's active subscription
     */
    @GetMapping("/active")
    @Operation(summary = "Get active subscription", description = "Get the current user's active subscription")
    public ResponseEntity<Map<String, Object>> getActiveSubscription(@RequestParam UUID userId) {
        log.info("Getting active subscription for user: {}", userId);

        try {
            return subscriptionService.getActiveSubscription(userId)
                    .map(subscription -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("subscription", subscription);
                        response.put("remainingSessions", subscription.getRemainingSessionsCount());
                        response.put("canBookSession", subscription.hasAvailableSessions());
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "No active subscription found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                    });

        } catch (Exception e) {
            log.error("Error getting active subscription: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get active subscription: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get all subscriptions for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user subscriptions", description = "Get all subscriptions for a user")
    public ResponseEntity<Map<String, Object>> getUserSubscriptions(@PathVariable UUID userId) {
        log.info("Getting subscriptions for user: {}", userId);

        try {
            List<Subscription> subscriptions = subscriptionService.getUserSubscriptions(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("subscriptions", subscriptions);
            response.put("count", subscriptions.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting user subscriptions: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get subscriptions: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get subscription by ID
     */
    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get subscription by ID", description = "Get details of a specific subscription")
    public ResponseEntity<Map<String, Object>> getSubscriptionById(@PathVariable UUID subscriptionId) {
        log.info("Getting subscription: {}", subscriptionId);

        try {
            Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("subscription", subscription);
            response.put("remainingSessions", subscription.getRemainingSessionsCount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            log.error("Error getting subscription: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get subscription: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Create a new subscription
     * For free plans: subscription is activated immediately
     * For paid plans: payment is initiated and subscription is pending
     */
    @PostMapping
    @Operation(summary = "Create subscription", description = "Create a new subscription for a user")
    public ResponseEntity<ApiResponse<SubscriptionCreationResponse>> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {

        log.info("Creating subscription for user {} with plan {}", request.getUserId(), request.getPlanId());

        try {
            ApiResponse<SubscriptionCreationResponse> response = subscriptionService.createSubscription(request);

            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

        } catch (Exception e) {
            log.error("Error creating subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create subscription: " + e.getMessage()));
        }
    }

    /**
     * Upgrade subscription
     */
    @PutMapping("/upgrade")
    @Operation(summary = "Upgrade subscription", description = "Upgrade user's subscription to a higher plan")
    public ResponseEntity<ApiResponse<Subscription>> upgradeSubscription(
            @Valid @RequestBody UpgradeSubscriptionRequest request) {

        log.info("Upgrading subscription for user {} to plan {}", request.getUserId(), request.getNewPlanId());

        try {
            ApiResponse<Subscription> response = subscriptionService.upgradeSubscription(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error upgrading subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to upgrade subscription: " + e.getMessage()));
        }
    }

    /**
     * Cancel subscription
     */
    @DeleteMapping("/cancel")
    @Operation(summary = "Cancel subscription", description = "Cancel user's active subscription")
    public ResponseEntity<ApiResponse<Void>> cancelSubscription(
            @Valid @RequestBody CancelSubscriptionRequest request) {
        log.info("Cancelling subscription for user: {}", request.getUserId());

        try {
            ApiResponse<Void> response = subscriptionService.cancelSubscription(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error cancelling subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cancel subscription: " + e.getMessage()));
        }
    }

    /**
     * Check if user can book a session
     */
    @GetMapping("/can-book")
    @Operation(summary = "Check if user can book", description = "Check if user has available sessions in their subscription")
    public ResponseEntity<Map<String, Object>> canBookSession(@RequestParam UUID userId) {
        log.info("Checking if user {} can book a session", userId);

        try {
            boolean canBook = subscriptionService.canBookSession(userId);
            int remainingSessions = subscriptionService.getRemainingSessionsCount(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("canBook", canBook);
            response.put("remainingSessions", remainingSessions);

            if (!canBook) {
                response.put("message", "Subscription limit reached. Please upgrade your plan or make a payment.");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking booking availability: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to check booking availability: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get available subscription plans
     */
    @GetMapping("/plans")
    @Operation(summary = "Get subscription plans", description = "Get list of available subscription plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getSubscriptionPlans(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        log.info("Getting subscription plans (includeInactive: {})", includeInactive);

        try {
            List<SubscriptionPlan> plans = includeInactive ?
                    subscriptionService.getAllPlans() :
                    subscriptionService.getActivePlans();

            return ResponseEntity.ok(ApiResponse.success("Subscription plans retrieved successfully", plans));

        } catch (Exception e) {
            log.error("Error getting subscription plans: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get subscription plans: " + e.getMessage()));
        }
    }

    /**
     * Get subscription plan by ID
     */
    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get subscription plan by ID", description = "Get details of a specific subscription plan")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> getSubscriptionPlanById(@PathVariable UUID planId) {
        log.info("Getting subscription plan: {}", planId);

        try {
            return subscriptionService.getPlanById(planId)
                    .map(plan -> ResponseEntity.ok(ApiResponse.success("Subscription plan retrieved successfully", plan)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Subscription plan not found")));

        } catch (Exception e) {
            log.error("Error getting subscription plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get subscription plan: " + e.getMessage()));
        }
    }

    /**
     * Create a new subscription plan
     */
    @PostMapping("/plans")
    @Operation(summary = "Create subscription plan", description = "Create a new subscription plan")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> createSubscriptionPlan(
            @Valid @RequestBody CreateSubscriptionPlanRequest request) {
        log.info("Creating subscription plan: {}", request.getName());

        try {
            ApiResponse<SubscriptionPlan> response = subscriptionService.createSubscriptionPlan(request);

            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }

        } catch (Exception e) {
            log.error("Error creating subscription plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create subscription plan: " + e.getMessage()));
        }
    }

    /**
     * Update an existing subscription plan
     */
    @PutMapping("/plans/{planId}")
    @Operation(summary = "Update subscription plan", description = "Update an existing subscription plan")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> updateSubscriptionPlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request) {
        log.info("Updating subscription plan: {}", planId);

        try {
            ApiResponse<SubscriptionPlan> response = subscriptionService.updateSubscriptionPlan(planId, request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error updating subscription plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update subscription plan: " + e.getMessage()));
        }
    }

    /**
     * Delete a subscription plan (soft delete)
     */
    @DeleteMapping("/plans/{planId}")
    @Operation(summary = "Delete subscription plan", description = "Deactivate a subscription plan (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteSubscriptionPlan(@PathVariable UUID planId) {
        log.info("Deleting subscription plan: {}", planId);

        try {
            ApiResponse<Void> response = subscriptionService.deleteSubscriptionPlan(planId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error deleting subscription plan: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete subscription plan: " + e.getMessage()));
        }
    }

    /**
     * Toggle subscription plan active status
     */
    @PatchMapping("/plans/{planId}/toggle-status")
    @Operation(summary = "Toggle plan status", description = "Toggle subscription plan active/inactive status")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> togglePlanStatus(@PathVariable UUID planId) {
        log.info("Toggling status for subscription plan: {}", planId);

        try {
            ApiResponse<SubscriptionPlan> response = subscriptionService.togglePlanActiveStatus(planId);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {
            log.error("Error toggling subscription plan status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to toggle subscription plan status: " + e.getMessage()));
        }
    }
}