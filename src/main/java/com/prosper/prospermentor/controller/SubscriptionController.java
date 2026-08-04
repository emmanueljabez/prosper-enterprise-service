package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.controller.dto.SessionDtos.SessionResponseDto;
import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionAddon;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.SessionBookingService;
import com.prosper.prospermentor.service.SubscriptionRenewalCoordinatorService;
import com.prosper.prospermentor.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final SessionBookingService sessionBookingService;
    private final SubscriptionRenewalCoordinatorService subscriptionRenewalCoordinatorService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  SessionBookingService sessionBookingService,
                                  SubscriptionRenewalCoordinatorService subscriptionRenewalCoordinatorService) {
        this.subscriptionService = subscriptionService;
        this.sessionBookingService = sessionBookingService;
        this.subscriptionRenewalCoordinatorService = subscriptionRenewalCoordinatorService;
    }

    /**
     * Get user's active subscription
     */
    @GetMapping("/active")
    @Operation(summary = "Get active subscription", description = "Get the current user's active subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActiveSubscription(@RequestParam UUID userId) {
        log.info("Getting active subscription for user: {}", userId);

        try {
            return subscriptionService.getEffectiveSubscriptionData(userId)
                    .map(data -> ResponseEntity.ok(ApiResponse.success("Active subscription retrieved successfully", data)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("No active subscription found")));

        } catch (Exception e) {
            log.error("Error getting active subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get active subscription: " + e.getMessage()));
        }
    }

    /**
     * Get all subscriptions for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user subscriptions", description = "Get all subscriptions for a user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserSubscriptions(@PathVariable UUID userId) {
        log.info("Getting subscriptions for user: {}", userId);

        try {
            List<Subscription> subscriptions = subscriptionService.getUserSubscriptions(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("subscriptions", subscriptions);
            data.put("count", subscriptions.size());

            return ResponseEntity.ok(ApiResponse.success("User subscriptions retrieved successfully", data));

        } catch (Exception e) {
            log.error("Error getting user subscriptions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get subscriptions: " + e.getMessage()));
        }
    }

    /**
     * Get subscription by ID
     */
    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get subscription by ID", description = "Get details of a specific subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubscriptionById(@PathVariable UUID subscriptionId) {
        log.info("Getting subscription: {}", subscriptionId);

        try {
            Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);

            Map<String, Object> data = new HashMap<>();
            data.put("subscription", subscription);
            data.put("remainingSessions", subscription.getRemainingSessionsCount());

            return ResponseEntity.ok(ApiResponse.success("Subscription retrieved successfully", data));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting subscription: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get subscription: " + e.getMessage()));
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
     * Upgrade subscription with proration
     */
    @PutMapping("/upgrade")
    @Operation(summary = "Upgrade subscription", description = "Upgrade user's subscription to a higher plan with prorated payment")
    public ResponseEntity<ApiResponse<SubscriptionUpgradeResponse>> upgradeSubscription(
            @Valid @RequestBody UpgradeSubscriptionRequest request) {

        log.info("Upgrading subscription for user {} to plan {}", request.getUserId(), request.getNewPlanId());

        try {
            ApiResponse<SubscriptionUpgradeResponse> response = subscriptionService.upgradeSubscription(request);

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
     * Apply a plan change immediately when no payment is required.
     */
    @PostMapping("/apply-plan")
    @Operation(summary = "Apply plan without payment", description = "Create or change a subscription immediately when no payment is required")
    public ResponseEntity<ApiResponse<Subscription>> applyPlanWithoutPayment(
            @Valid @RequestBody ApplyPlanRequest request) {

        log.info("Applying plan {} for user {} without payment", request.getPlanId(), request.getUserId());

        try {
            ApiResponse<Subscription> response = subscriptionService.applyZeroCostPlanChange(
                    request.getUserId(),
                    request.getPlanId(),
                    request.getBillingInterval()
            );

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (Exception e) {
            log.error("Error applying plan without payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to apply plan: " + e.getMessage()));
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
     * Trigger a manual renewal using invoice-first flow.
     * If automatic card charge is possible it completes immediately, otherwise
     * an invoice/payment URL is returned for user checkout.
     */
    @PostMapping("/renew-now")
    @Operation(summary = "Renew subscription now", description = "Create renewal invoice and attempt automatic charge when possible")
    public ResponseEntity<ApiResponse<Map<String, Object>>> renewSubscriptionNow(
            @Valid @RequestBody RenewNowRequest request) {
        log.info("Manual renew requested for user {}", request.getUserId());

        try {
            SubscriptionRenewalCoordinatorService.RenewalExecutionResult result =
                    subscriptionRenewalCoordinatorService.initiateManualRenewal(request.getUserId());

            Map<String, Object> data = new HashMap<>();
            data.put("chargedAutomatically", result.isChargedAutomatically());
            data.put("invoiceId", result.getInvoiceId());
            data.put("invoiceNumber", result.getInvoiceNumber());
            data.put("paymentUrl", result.getPaymentUrl());
            data.put("paymentId", result.getPaymentId());
            data.put("transactionId", result.getTransactionId());
            data.put("renewed", result.isSuccess());
            data.put("requiresManualPayment", !result.isSuccess() && result.getPaymentUrl() != null);

            if (result.isSuccess() || result.getPaymentUrl() != null) {
                return ResponseEntity.ok(ApiResponse.success(result.getMessage(), data));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(result.getMessage(), data));
        } catch (Exception e) {
            log.error("Error processing manual renewal for user {}: {}", request.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to process renewal: " + e.getMessage()));
        }
    }

    /**
     * Enable or disable auto-renew for a user's active subscription.
     */
    @PatchMapping("/auto-renew")
    @Operation(summary = "Update auto-renew preference", description = "Enable or disable auto-renew for active subscription")
    public ResponseEntity<ApiResponse<Subscription>> updateAutoRenew(
            @Valid @RequestBody UpdateAutoRenewRequest request) {
        log.info("Updating auto-renew for user {} to {}", request.getUserId(), request.getAutoRenew());

        try {
            ApiResponse<Subscription> response = subscriptionService.updateAutoRenewPreference(
                    request.getUserId(),
                    request.getAutoRenew()
            );

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            if ("No active subscription found for user".equals(response.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error updating auto-renew for user {}: {}", request.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update auto-renew preference: " + e.getMessage()));
        }
    }

    /**
     * Check if user can book a session
     */
    @GetMapping("/can-book")
    @Operation(summary = "Check if user can book", description = "Check if user has available sessions in their subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> canBookSession(@RequestParam UUID userId) {
        log.info("Checking if user {} can book a session", userId);

        try {
            SessionBookingEligibility eligibility = subscriptionService.checkSessionBookingEligibility(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("canBook", eligibility.isCanBook());
            data.put("remainingSessions", eligibility.getSessionsRemaining());
            data.put("addonSessionsRemaining", eligibility.getAddonSessionsRemaining());
            data.put("subscriptionSource", eligibility.getSubscriptionSource());
            data.put("companyId", eligibility.getCompanyId());
            data.put("companySubscriptionId", eligibility.getCompanySubscriptionId());
            data.put("nextBillingDate", eligibility.getNextBillingDate());
            data.put("reason", eligibility.getReason());
            data.put("recommendedPlans", eligibility.getRecommendedPlans());
            data.put("addOnOption", eligibility.getAddOnOption());

            String message = eligibility.isCanBook()
                ? "Booking availability checked successfully"
                : eligibility.getMessage();

            return ResponseEntity.ok(ApiResponse.success(message, data));

        } catch (Exception e) {
            log.error("Error checking booking availability: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check booking availability: " + e.getMessage()));
        }
    }

    /**
     * Get available subscription plans
     */
    @GetMapping("/plans")
    @Operation(summary = "Get subscription plans", description = "Get list of available subscription plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getSubscriptionPlans(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String audience) {
        log.info("Getting subscription plans (includeInactive: {}, audience: {})", includeInactive, audience);

        try {
            List<SubscriptionPlan> plans = includeInactive ?
                    subscriptionService.getAllPlans(audience) :
                    subscriptionService.getActivePlans(audience);

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

    // ========== FEATURE-BASED ACCESS ENDPOINTS ==========

    /**
     * Get user's features with details
     */
    @GetMapping("/features")
    @Operation(summary = "Get user features", description = "Get all features available to the user with their subscription")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserFeatures(@RequestParam UUID userId) {
        log.info("Getting features for user: {}", userId);

        try {
            Map<String, Object> features = subscriptionService.getUserFeatures(userId);

            return ResponseEntity.ok(ApiResponse.success("User features retrieved successfully", features));

        } catch (Exception e) {
            log.error("Error getting user features: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get user features: " + e.getMessage()));
        }
    }

    /**
     * Check if user can access a specific feature
     */
    @GetMapping("/features/{featureCode}/check")
    @Operation(summary = "Check feature access", description = "Check if user has access to a specific feature")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFeatureAccess(
            @RequestParam UUID userId,
            @PathVariable String featureCode) {
        log.info("Checking feature {} access for user: {}", featureCode, userId);

        try {
            boolean hasAccess = subscriptionService.canAccessFeature(userId, featureCode);
            Integer limit = subscriptionService.getUserFeatureLimit(userId, featureCode);

            Map<String, Object> data = new HashMap<>();
            data.put("hasAccess", hasAccess);
            data.put("featureCode", featureCode);
            data.put("limit", limit);
            data.put("unlimited", limit == -1);

            String message = hasAccess ?
                "Feature access checked successfully" :
                "Upgrade your subscription to access this feature";

            return ResponseEntity.ok(ApiResponse.success(message, data));

        } catch (Exception e) {
            log.error("Error checking feature access: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to check feature access: " + e.getMessage()));
        }
    }

    // ========== ADD-ON ENDPOINTS ==========

    /**
     * Get user's add-ons
     */
    @GetMapping("/addons")
    @Operation(summary = "Get user add-ons", description = "Get all add-ons purchased by the user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserAddons(@RequestParam UUID userId) {
        log.info("Getting add-ons for user: {}", userId);

        try {
            List<SubscriptionAddon> addons = subscriptionService.getUserAddons(userId);

            // Calculate totals
            int totalRemaining = addons.stream()
                    .filter(SubscriptionAddon::hasRemaining)
                    .mapToInt(SubscriptionAddon::getRemaining)
                    .sum();

            Map<String, Object> data = new HashMap<>();
            data.put("addons", addons);
            data.put("totalRemaining", totalRemaining);
            data.put("count", addons.size());

            return ResponseEntity.ok(ApiResponse.success("User add-ons retrieved successfully", data));

        } catch (Exception e) {
            log.error("Error getting user add-ons: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get user add-ons: " + e.getMessage()));
        }
    }

    /**
     * Purchase add-on sessions
     */
    @PostMapping("/addons/purchase")
    @Operation(summary = "Purchase add-on sessions", description = "Purchase extra sessions beyond subscription limit")
    public ResponseEntity<ApiResponse<PurchaseAddonResponse>> purchaseAddonSessions(
            @Valid @RequestBody PurchaseAddonRequest request) {
        log.info("User {} purchasing {} add-on sessions", request.getUserId(), request.getQuantity());

        try {
            ApiResponse<PurchaseAddonResponse> response = subscriptionService.purchaseAddonSessions(
                    request.getUserId(), request.getQuantity(), request.getPhoneNumber(), request.getCurrency());

            if (response.isSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

        } catch (Exception e) {
            log.error("Error purchasing add-on sessions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to purchase add-on sessions: " + e.getMessage()));
        }
    }

    /**
     * Get add-on sessions remaining count
     */
    @GetMapping("/addons/remaining")
    @Operation(summary = "Get remaining add-on sessions", description = "Get count of remaining add-on sessions for user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAddonSessionsRemaining(@RequestParam UUID userId) {
        log.info("Getting remaining add-on sessions for user: {}", userId);

        try {
            subscriptionService.getActiveSubscription(userId)
                    .ifPresentOrElse(
                            subscription -> {
                                // Subscription found
                            },
                            () -> {
                                throw new IllegalStateException("No active subscription found");
                            }
                    );

            int remainingSessions = subscriptionService.getActiveSubscription(userId)
                    .map(sub -> subscriptionService.getAddonSessionsRemaining(sub.getId()))
                    .orElse(0);

            Map<String, Object> data = new HashMap<>();
            data.put("remainingAddons", remainingSessions);

            return ResponseEntity.ok(ApiResponse.success("Remaining add-on sessions retrieved successfully", data));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting remaining add-on sessions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get remaining add-on sessions: " + e.getMessage()));
        }
    }

    /**
     * Get mentee sessions with pagination and filtering
     */
    @GetMapping("/mentee/sessions")
    @Operation(summary = "Get mentee sessions",
               description = "Get sessions for a mentee with pagination and filtering support. " +
                            "Filter options: 'all' (default), 'today', 'upcoming', 'past'")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMenteeSessions(
            @RequestParam UUID menteeId,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("Getting sessions for mentee: {} with filter: {} (page: {}, size: {})", menteeId, filter, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Session> sessionsPage = sessionBookingService.getMenteeSessionsWithFilter(menteeId, filter, pageable);

            Map<String, Object> data = new HashMap<>();
            data.put("sessions", sessionsPage.getContent().stream()
                    .map(this::toSessionResponseDto)
                    .toList());
            data.put("currentPage", sessionsPage.getNumber());
            data.put("totalPages", sessionsPage.getTotalPages());
            data.put("totalSessions", sessionsPage.getTotalElements());
            data.put("hasNext", sessionsPage.hasNext());
            data.put("hasPrevious", sessionsPage.hasPrevious());
            data.put("filter", filter);

            return ResponseEntity.ok(ApiResponse.success("Mentee sessions retrieved successfully", data));

        } catch (IllegalArgumentException e) {
            log.error("Invalid filter parameter: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting mentee sessions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to get mentee sessions: " + e.getMessage()));
        }
    }

    private SessionResponseDto toSessionResponseDto(Session session) {
        boolean paymentRequired = !sessionBookingService.canUserBookSession(session.getMenteeId());

        return SessionResponseDto.builder()
                .id(session.getId())
                .mentorId(session.getMentorId())
                .menteeId(session.getMenteeId())
                .skillId(session.getSkillId())
                .companyProgramId(session.getCompanyProgramId())
                .companyProgramParticipantId(session.getCompanyProgramParticipantId())
                .title(session.getTitle())
                .description(session.getDescription())
                .scheduledStart(session.getScheduledStart())
                .scheduledEnd(session.getScheduledEnd())
                .status(session.getStatus())
                .meetingPlatform(session.getMeetingPlatform())
                .meetingUrl(session.getMeetingUrl())
                .meetingId(session.getMeetingId())
                .meetingPassword(session.getMeetingPassword())
                .price(session.getPrice())
                .currency(session.getCurrency())
                .paymentStatus(session.getPaymentStatus())
                .paymentRequired(paymentRequired)
                .menteeMessage(session.getMenteeMessage())
                .mentorResponse(session.getMentorResponse())
                .calendarEventId(session.getCalendarEventId())
                .confirmedAt(session.getConfirmedAt())
                .cancelledAt(session.getCancelledAt())
                .cancellationReason(session.getCancellationReason())
                .cancelledBy(session.getCancelledBy())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .companyProgramName(session.getCompanyProgram() != null ? session.getCompanyProgram().getName() : null)
                .durationMinutes(session.getScheduledStart() != null && session.getScheduledEnd() != null
                        ? session.getDurationMinutes()
                        : 0)
                .canBeModified(session.canBeModified())
                .isFutureBooking(session.getScheduledStart() != null && session.isFutureSession())
                .build();
    }

    public static class RenewNowRequest {
        @jakarta.validation.constraints.NotNull
        private UUID userId;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }
    }

    public static class ApplyPlanRequest {
        @jakarta.validation.constraints.NotNull
        private UUID userId;

        @jakarta.validation.constraints.NotNull
        private UUID planId;

        @jakarta.validation.constraints.NotNull
        private BillingInterval billingInterval = BillingInterval.MONTHLY;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public UUID getPlanId() {
            return planId;
        }

        public void setPlanId(UUID planId) {
            this.planId = planId;
        }

        public BillingInterval getBillingInterval() {
            return billingInterval;
        }

        public void setBillingInterval(BillingInterval billingInterval) {
            this.billingInterval = billingInterval;
        }
    }

    public static class UpdateAutoRenewRequest {
        @jakarta.validation.constraints.NotNull
        private UUID userId;

        @jakarta.validation.constraints.NotNull
        private Boolean autoRenew;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
            this.userId = userId;
        }

        public Boolean getAutoRenew() {
            return autoRenew;
        }

        public void setAutoRenew(Boolean autoRenew) {
            this.autoRenew = autoRenew;
        }
    }
}
