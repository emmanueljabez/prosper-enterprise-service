package com.prosper.prospermentor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing whether a user is eligible to book a session and why
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionBookingEligibility {

    /**
     * Whether the user can book a session
     */
    private boolean canBook;

    /**
     * Detailed message explaining the eligibility status
     */
    private String message;

    /**
     * Reason code for programmatic handling
     */
    private EligibilityReason reason;

    /**
     * Number of sessions remaining (if applicable)
     */
    private Integer sessionsRemaining;

    /**
     * Number of add-on sessions remaining (if applicable)
     */
    private Integer addonSessionsRemaining;

    /**
     * List of recommended plans to upgrade to (when user is not eligible)
     * Ordered by display order, showing the most relevant plans first
     */
    private List<RecommendedPlanDto> recommendedPlans;

    /**
     * Add-on session purchase option (for users who exhausted sessions on max-tier plans)
     */
    private AddOnSessionDto addOnOption;

    /**
     * Which entitlement source will be used for the next booking attempt.
     */
    private SubscriptionSource subscriptionSource;

    /**
     * Company identifier when the effective entitlement is corporate-sponsored.
     */
    private UUID companyId;

    /**
     * Company subscription identifier when the effective entitlement is corporate-sponsored.
     */
    private UUID companySubscriptionId;

    /**
     * End of the current billing period for the active entitlement source.
     */
    private LocalDateTime nextBillingDate;

    /**
     * Create an eligible response
     */
    public static SessionBookingEligibility eligible(String message, Integer sessionsRemaining, Integer addonSessionsRemaining) {
        return SessionBookingEligibility.builder()
                .canBook(true)
                .message(message)
                .reason(EligibilityReason.ELIGIBLE)
                .sessionsRemaining(sessionsRemaining)
                .addonSessionsRemaining(addonSessionsRemaining)
                .build();
    }

    /**
     * Create an ineligible response
     */
    public static SessionBookingEligibility ineligible(String message, EligibilityReason reason) {
        return SessionBookingEligibility.builder()
                .canBook(false)
                .message(message)
                .reason(reason)
                .build();
    }

    /**
     * Create an ineligible response with recommended plans
     */
    public static SessionBookingEligibility ineligible(String message, EligibilityReason reason, List<RecommendedPlanDto> recommendedPlans) {
        return SessionBookingEligibility.builder()
                .canBook(false)
                .message(message)
                .reason(reason)
                .recommendedPlans(recommendedPlans)
                .build();
    }

    /**
     * Create an ineligible response with add-on option (for max-tier users who exhausted sessions)
     */
    public static SessionBookingEligibility ineligibleWithAddOn(String message, EligibilityReason reason, AddOnSessionDto addOnOption) {
        return SessionBookingEligibility.builder()
                .canBook(false)
                .message(message)
                .reason(reason)
                .addOnOption(addOnOption)
                .build();
    }

    /**
     * Create an ineligible response with both add-on option and recommended plans
     */
    public static SessionBookingEligibility ineligible(String message, EligibilityReason reason, List<RecommendedPlanDto> recommendedPlans, AddOnSessionDto addOnOption) {
        return SessionBookingEligibility.builder()
                .canBook(false)
                .message(message)
                .reason(reason)
                .recommendedPlans(recommendedPlans)
                .addOnOption(addOnOption)
                .build();
    }

    /**
     * Reasons why a user may or may not be eligible to book a session
     */
    public enum EligibilityReason {
        ELIGIBLE,                    // User can book
        NO_ACTIVE_SUBSCRIPTION,      // User has no active subscription
        FEATURE_NOT_AVAILABLE,       // User's plan doesn't include 1:1 mentor sessions
        SESSIONS_EXHAUSTED,          // User has used all sessions and has no add-ons
        PLAN_EXPIRED                 // User's subscription has expired
    }

    public enum SubscriptionSource {
        CORPORATE,
        INDIVIDUAL,
        PERSONAL_CREDIT
    }
}
