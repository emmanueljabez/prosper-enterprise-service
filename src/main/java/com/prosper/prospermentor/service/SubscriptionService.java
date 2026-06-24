package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.*;
import com.prosper.prospermentor.entity.*;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing user subscriptions
 */
@Service
@Slf4j
@Transactional
public class SubscriptionService {

    public static final int DEFAULT_SESSION_DURATION_MINUTES = 60;
    public static final int TRIAL_SESSION_DURATION_MINUTES = 30;
    private static final Set<String> PUBLIC_MENTEE_SESSION_PACKAGE_CODES = Set.of(
            "SINGLE_SESSION",
            "PACK_3",
            "PACK_5",
            "PACK_10",
            "THREE_SESSION_PACK",
            "FIVE_SESSION_PACK",
            "TEN_SESSION_PACK"
    );

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final MpesaService mpesaService;
    private final com.prosper.prospermentor.repository.SubscriptionAddonRepository addonRepository;
    private final com.prosper.prospermentor.repository.FeatureRepository featureRepository;
    private final CurrencyService currencyService;
    private final CompanySubscriptionService companySubscriptionService;
    private final EmployeeSessionAllocationService employeeSessionAllocationService;
    private final PersonalSessionCreditService personalSessionCreditService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionPlanRepository subscriptionPlanRepository,
                               MpesaService mpesaService,
                               com.prosper.prospermentor.repository.SubscriptionAddonRepository addonRepository,
                               com.prosper.prospermentor.repository.FeatureRepository featureRepository,
                               CurrencyService currencyService,
                               CompanySubscriptionService companySubscriptionService,
                               EmployeeSessionAllocationService employeeSessionAllocationService,
                               PersonalSessionCreditService personalSessionCreditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.mpesaService = mpesaService;
        this.addonRepository = addonRepository;
        this.featureRepository = featureRepository;
        this.currencyService = currencyService;
        this.companySubscriptionService = companySubscriptionService;
        this.employeeSessionAllocationService = employeeSessionAllocationService;
        this.personalSessionCreditService = personalSessionCreditService;
    }

    public enum SessionConsumptionSource {
        CORPORATE_ALLOCATION,
        CORPORATE_SUBSCRIPTION,
        PERSONAL_CREDIT,
        INDIVIDUAL_SUBSCRIPTION,
        SUBSCRIPTION_ADDON
    }

    public record SessionConsumptionResult(
            SessionConsumptionSource source,
            UUID subscriptionId,
            UUID addonId
    ) {
        public static SessionConsumptionResult individualSubscription(UUID subscriptionId) {
            return new SessionConsumptionResult(SessionConsumptionSource.INDIVIDUAL_SUBSCRIPTION, subscriptionId, null);
        }

        public static SessionConsumptionResult subscriptionAddon(UUID subscriptionId, UUID addonId) {
            return new SessionConsumptionResult(SessionConsumptionSource.SUBSCRIPTION_ADDON, subscriptionId, addonId);
        }

        public static SessionConsumptionResult personalCredit() {
            return new SessionConsumptionResult(SessionConsumptionSource.PERSONAL_CREDIT, null, null);
        }

        public static SessionConsumptionResult corporateAllocation() {
            return new SessionConsumptionResult(SessionConsumptionSource.CORPORATE_ALLOCATION, null, null);
        }

        public static SessionConsumptionResult corporateSubscription() {
            return new SessionConsumptionResult(SessionConsumptionSource.CORPORATE_SUBSCRIPTION, null, null);
        }
    }

    /**
     * Get active subscription for a user
     */
    @Transactional(readOnly = true)
    public Optional<Subscription> getActiveSubscription(UUID userId) {
        return getActiveIndividualSubscription(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> getActiveIndividualSubscription(UUID userId) {
        return subscriptionRepository.findActiveSubscriptionByUserId(userId, LocalDateTime.now());
    }

    /**
     * Check if user can book a session with detailed eligibility information
     * This method checks:
     * 1. If user has an active subscription
     * 2. If the subscription plan includes MENTOR_SESSION feature
     * 3. If user has available sessions (subscription or add-ons)
     */
    @Transactional(readOnly = true)
    public SessionBookingEligibility checkSessionBookingEligibility(UUID userId) {
        Optional<ResolvedEntitlement> effectiveEntitlementOpt = resolveEffectiveEntitlement(userId);
        if (effectiveEntitlementOpt.isPresent()) {
            return effectiveEntitlementOpt.get().eligibility;
        }

        return buildNoActiveSubscriptionEligibility(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getEffectiveSubscriptionData(UUID userId) {
        return resolveEffectiveEntitlement(userId).map(this::buildEffectiveSubscriptionPayload);
    }

    public ApiResponse<Subscription> activateFreeTrial(UUID userId) {
        if (userId == null) {
            return ApiResponse.error("User ID is required");
        }

        List<Subscription> subscriptionHistory = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Optional<Subscription> activeTrial = subscriptionHistory.stream()
                .filter(this::isTrialSubscription)
                .filter(Subscription::isActive)
                .findFirst();
        if (activeTrial.isPresent()) {
            return ApiResponse.success("Free trial is already active", activeTrial.get());
        }

        boolean alreadyUsedTrial = subscriptionHistory.stream().anyMatch(this::isTrialSubscription);
        if (alreadyUsedTrial) {
            return ApiResponse.error("Free trial already used for this account");
        }

        Optional<Subscription> activeIndividualSubscription = getActiveIndividualSubscription(userId);
        if (activeIndividualSubscription.isPresent()) {
            return ApiResponse.error("User already has an active subscription");
        }

        Optional<SubscriptionPlan> trialPlanOpt = resolveFreeTrialPlan();
        if (trialPlanOpt.isEmpty()) {
            return ApiResponse.error("Free trial plan is not configured");
        }

        SubscriptionPlan trialPlan = trialPlanOpt.get();
        if (!Boolean.TRUE.equals(trialPlan.getIsActive())) {
            return ApiResponse.error("Free trial plan is not active");
        }
        if (!trialPlan.supportsIndividualPurchases()) {
            return ApiResponse.error("Free trial plan is not available for individual users");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusDays(30);

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(trialPlan);
        subscription.setBillingInterval(BillingInterval.MONTHLY);
        subscription.setSessionsPerMonth(Math.max(1, trialPlan.getSessionsPerPeriod() != null ? trialPlan.getSessionsPerPeriod() : 1));
        subscription.setSessionsUsed(0);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(endDate);
        subscription.setStatus(Subscription.SubscriptionStatus.TRIAL);
        subscription.setAutoRenew(false);
        subscription.setIsTrial(true);

        Subscription savedSubscription = subscriptionRepository.save(subscription);
        log.info("Activated free trial subscription {} for user {}", savedSubscription.getId(), userId);
        return ApiResponse.success("Free trial activated", savedSubscription);
    }

    private Optional<ResolvedEntitlement> resolveEffectiveEntitlement(UUID userId) {
        Optional<ResolvedEntitlement> corporateEntitlement = resolveCorporateEntitlement(userId);
        // For company-sponsored employees, the company seat is the source of truth for mentor-session gating.
        // If the seat exists but is exhausted or misconfigured, do not silently fall back to an individual plan.
        if (corporateEntitlement.isPresent()) {
            return corporateEntitlement;
        }

        Optional<ResolvedEntitlement> personalCreditEntitlement = resolvePersonalCreditEntitlement(userId);
        if (personalCreditEntitlement.isPresent()) {
            return personalCreditEntitlement;
        }

        Optional<ResolvedEntitlement> individualEntitlement = resolveIndividualEntitlement(userId);
        if (individualEntitlement.isPresent() && individualEntitlement.get().eligibility.isCanBook()) {
            return individualEntitlement;
        }

        return individualEntitlement;
    }

    private Optional<ResolvedEntitlement> resolveCorporateEntitlement(UUID userId) {
        Optional<ResolvedEntitlement> allocationEntitlement = employeeSessionAllocationService.findActiveAllocationForProfile(userId)
                .map(allocation -> {
                    SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                            "You can book a session using your company-funded allocation.",
                            allocation.getAvailableBalance(),
                            0
                    );
                    eligibility.setSubscriptionSource(SessionBookingEligibility.SubscriptionSource.CORPORATE);
                    eligibility.setCompanyId(allocation.getCompany() != null ? allocation.getCompany().getId() : null);
                    eligibility.setSessionDurationMinutes(DEFAULT_SESSION_DURATION_MINUTES);
                    return new ResolvedEntitlement(
                            eligibility,
                            null,
                            null,
                            null,
                            allocation.getAvailableBalance(),
                            0,
                            allocation
                    );
                });

        if (allocationEntitlement.isPresent()) {
            return allocationEntitlement;
        }

        return companySubscriptionService.findActiveMembershipForUser(userId)
                .map(member -> {
                    SessionBookingEligibility eligibility = buildCorporateEligibility(member);
                    return new ResolvedEntitlement(
                            eligibility,
                            null,
                            member,
                            member.getCompanySubscription() != null ? member.getCompanySubscription().getPlan() : null,
                            member.getRemainingSessionsCount(),
                            0,
                            null
                    );
                });
    }

    private Optional<ResolvedEntitlement> resolvePersonalCreditEntitlement(UUID userId) {
        int availableCredits = personalSessionCreditService.getAvailableCreditCount(userId);
        if (availableCredits < 1) {
            return Optional.empty();
        }

        SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                String.format("You have %d credited session%s available.", availableCredits, availableCredits == 1 ? "" : "s"),
                availableCredits,
                0
        );
        eligibility.setSubscriptionSource(SessionBookingEligibility.SubscriptionSource.PERSONAL_CREDIT);
        eligibility.setSessionDurationMinutes(DEFAULT_SESSION_DURATION_MINUTES);

        return Optional.of(new ResolvedEntitlement(
                eligibility,
                null,
                null,
                null,
                availableCredits,
                0,
                null,
                availableCredits
        ));
    }

    private Optional<ResolvedEntitlement> resolveIndividualEntitlement(UUID userId) {
        return getActiveIndividualSubscription(userId)
                .map(subscription -> {
                    SessionBookingEligibility eligibility = buildIndividualEligibility(subscription, userId);
                    return new ResolvedEntitlement(
                            eligibility,
                            subscription,
                            null,
                            subscription.getPlan(),
                            subscription.getPlan() != null && subscription.getPlan().isUnlimited()
                                    ? Integer.MAX_VALUE
                                    : subscription.getRemainingSessionsCount(),
                            getAddonSessionsRemaining(subscription.getId()),
                            null
                    );
                });
    }

    private SessionBookingEligibility buildNoActiveSubscriptionEligibility(UUID userId) {
        log.info("User {} has no active corporate or individual subscription", userId);

        List<RecommendedPlanDto> recommendedPlans = getRecommendedPlansWithMentorSessions(null);
        List<Subscription> allSubscriptions = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (!allSubscriptions.isEmpty()) {
            Subscription lastSubscription = allSubscriptions.get(0);
            String planName = lastSubscription.getPlan() != null ? lastSubscription.getPlan().getName() : "previous";
            return SessionBookingEligibility.ineligible(
                    String.format("Your '%s' subscription has expired or is no longer active. Please renew your subscription or upgrade to a plan that includes 1:1 mentor sessions.", planName),
                    SessionBookingEligibility.EligibilityReason.PLAN_EXPIRED,
                    recommendedPlans
            );
        }

        return SessionBookingEligibility.ineligible(
                "You don't have an active subscription. Please subscribe to a plan that includes 1:1 mentor sessions to book sessions.",
                SessionBookingEligibility.EligibilityReason.NO_ACTIVE_SUBSCRIPTION,
                recommendedPlans
        );
    }

    private SessionBookingEligibility buildCorporateEligibility(CompanySubscriptionMember member) {
        if (member == null || member.getCompanySubscription() == null || member.getCompanySubscription().getPlan() == null) {
            return SessionBookingEligibility.ineligible(
                    "Your corporate sponsorship is not configured correctly. Please contact your company admin.",
                    SessionBookingEligibility.EligibilityReason.NO_ACTIVE_SUBSCRIPTION
            );
        }

        SubscriptionPlan plan = member.getCompanySubscription().getPlan();
        if (!hasMentorSessionFeature(plan)) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.ineligible(
                    String.format("Your company-sponsored '%s' plan does not include 1:1 mentor sessions.", plan.getName()),
                    SessionBookingEligibility.EligibilityReason.FEATURE_NOT_AVAILABLE,
                    getRecommendedPlansWithMentorSessions(plan.getId())
            );
            eligibility.setSessionsRemaining(member.getRemainingSessionsCount());
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.CORPORATE, member);
        }

        if (plan.isUnlimited()) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                    "Your company sponsorship includes unlimited mentor sessions.",
                    Integer.MAX_VALUE,
                    0
            );
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.CORPORATE, member);
        }

        int remainingSessions = member.getRemainingSessionsCount();
        if (remainingSessions > 0) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                    String.format("You can book a session using your company-sponsored plan. Sessions remaining: %d", remainingSessions),
                    remainingSessions,
                    0
            );
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.CORPORATE, member);
        }

        SessionBookingEligibility eligibility = SessionBookingEligibility.ineligible(
                String.format("You have used all sessions in your company-sponsored '%s' plan for this billing period.", plan.getName()),
                SessionBookingEligibility.EligibilityReason.SESSIONS_EXHAUSTED
        );
        eligibility.setSessionsRemaining(0);
        return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.CORPORATE, member);
    }

    private SessionBookingEligibility buildIndividualEligibility(Subscription subscription, UUID userId) {
        SubscriptionPlan plan = subscription.getPlan();
        if (plan == null) {
            return SessionBookingEligibility.ineligible(
                    "Your subscription plan is not configured properly. Please contact support.",
                    SessionBookingEligibility.EligibilityReason.NO_ACTIVE_SUBSCRIPTION
            );
        }

        if (!hasMentorSessionFeature(plan)) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.ineligible(
                    String.format("Your current '%s' plan does not include 1:1 mentor sessions. Please upgrade to a higher tier plan that includes this feature to book sessions with mentors.", plan.getName()),
                    SessionBookingEligibility.EligibilityReason.FEATURE_NOT_AVAILABLE,
                    getRecommendedPlansWithMentorSessions(plan.getId())
            );
            eligibility.setSessionsRemaining(subscription.getRemainingSessionsCount());
            eligibility.setAddonSessionsRemaining(getAddonSessionsRemaining(subscription.getId()));
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.INDIVIDUAL, subscription);
        }

        if (plan.isUnlimited()) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                    "You have unlimited sessions available.",
                    Integer.MAX_VALUE,
                    0
            );
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.INDIVIDUAL, subscription);
        }

        int remainingSessions = subscription.getRemainingSessionsCount();
        int addonSessions = getAddonSessionsRemaining(subscription.getId());

        if (remainingSessions > 0 || addonSessions > 0) {
            SessionBookingEligibility eligibility = SessionBookingEligibility.eligible(
                    String.format(
                            "You can book a session. Sessions remaining: %d (subscription: %d, add-ons: %d)",
                            remainingSessions + addonSessions,
                            remainingSessions,
                            addonSessions
                    ),
                    remainingSessions,
                    addonSessions
            );
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.INDIVIDUAL, subscription);
        }

        if (Boolean.TRUE.equals(plan.getAllowsAddons()) && plan.getAddonSessionCost() != null) {
            AddOnSessionDto addOnOption = AddOnSessionDto.available(
                    plan.getAddonSessionCost(),
                    plan.getCurrency(),
                    5
            );
            SessionBookingEligibility eligibility = SessionBookingEligibility.ineligibleWithAddOn(
                    String.format("You have used all your sessions (%d/%d) in your '%s' plan. Purchase additional sessions to continue booking with mentors.",
                            subscription.getSessionsUsed(),
                            subscription.getSessionsPerMonth(),
                            plan.getName()),
                    SessionBookingEligibility.EligibilityReason.SESSIONS_EXHAUSTED,
                    addOnOption
            );
            eligibility.setSessionsRemaining(0);
            eligibility.setAddonSessionsRemaining(0);
            return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.INDIVIDUAL, subscription);
        }

        SessionBookingEligibility eligibility = SessionBookingEligibility.ineligible(
                String.format("You have used all your sessions (%d/%d) in your '%s' plan. Please upgrade to a higher tier plan for more sessions.",
                        subscription.getSessionsUsed(),
                        subscription.getSessionsPerMonth(),
                        plan.getName()),
                SessionBookingEligibility.EligibilityReason.SESSIONS_EXHAUSTED,
                getRecommendedPlansWithMentorSessions(plan.getId())
        );
        eligibility.setSessionsRemaining(0);
        eligibility.setAddonSessionsRemaining(0);
        return withEntitlementMetadata(eligibility, SessionBookingEligibility.SubscriptionSource.INDIVIDUAL, subscription);
    }

    private boolean hasMentorSessionFeature(SubscriptionPlan plan) {
        return plan != null && plan.getPlanFeatures().stream()
                .anyMatch(pf -> pf.getFeature().getType() == Feature.FeatureType.MENTOR_SESSION
                        && pf.getEnabled()
                        && pf.isAvailable());
    }

    private SessionBookingEligibility withEntitlementMetadata(SessionBookingEligibility eligibility,
                                                              SessionBookingEligibility.SubscriptionSource source,
                                                              Subscription subscription) {
        eligibility.setSubscriptionSource(source);
        eligibility.setNextBillingDate(subscription != null ? subscription.getCurrentPeriodEnd() : null);
        eligibility.setSessionDurationMinutes(resolveSessionDurationMinutes(subscription));
        return eligibility;
    }

    private SessionBookingEligibility withEntitlementMetadata(SessionBookingEligibility eligibility,
                                                              SessionBookingEligibility.SubscriptionSource source,
                                                              CompanySubscriptionMember member) {
        eligibility.setSubscriptionSource(source);
        eligibility.setSessionDurationMinutes(resolveSessionDurationMinutes(
                member != null && member.getCompanySubscription() != null
                        ? member.getCompanySubscription().getPlan()
                        : null
        ));
        if (member != null && member.getCompanySubscription() != null) {
            eligibility.setCompanyId(member.getCompanySubscription().getCompany() != null
                    ? member.getCompanySubscription().getCompany().getId()
                    : null);
            eligibility.setCompanySubscriptionId(member.getCompanySubscription().getId());
            eligibility.setNextBillingDate(member.getCompanySubscription().getCurrentPeriodEnd());
        }
        return eligibility;
    }

    private int resolveSessionDurationMinutes(Subscription subscription) {
        if (subscription == null) {
            return DEFAULT_SESSION_DURATION_MINUTES;
        }

        if (Boolean.TRUE.equals(subscription.getIsTrial())
                || subscription.getStatus() == Subscription.SubscriptionStatus.TRIAL
                || isFreeTrialPlan(subscription.getPlan())) {
            return TRIAL_SESSION_DURATION_MINUTES;
        }

        return resolveSessionDurationMinutes(subscription.getPlan());
    }

    private int resolveSessionDurationMinutes(SubscriptionPlan plan) {
        return isFreeTrialPlan(plan) ? TRIAL_SESSION_DURATION_MINUTES : DEFAULT_SESSION_DURATION_MINUTES;
    }

    private boolean isFreeTrialPlan(SubscriptionPlan plan) {
        return plan != null && "FREE_TRIAL".equalsIgnoreCase(String.valueOf(plan.getCode()));
    }

    private Optional<SubscriptionPlan> resolveFreeTrialPlan() {
        return subscriptionPlanRepository.findByCode("FREE_TRIAL")
                .or(() -> subscriptionPlanRepository.findByCode("ALL_ACCESS"));
    }

    private boolean isTrialSubscription(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        return Boolean.TRUE.equals(subscription.getIsTrial())
                || subscription.getStatus() == Subscription.SubscriptionStatus.TRIAL
                || isFreeTrialPlan(subscription.getPlan());
    }

    private Map<String, Object> buildEffectiveSubscriptionPayload(ResolvedEntitlement entitlement) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subscriptionSource", entitlement.eligibility.getSubscriptionSource());
        payload.put("remainingSessions", entitlement.eligibility.getSessionsRemaining());
        payload.put("addonSessionsRemaining", entitlement.eligibility.getAddonSessionsRemaining());
        payload.put("canBookSession", entitlement.eligibility.isCanBook());
        payload.put("nextBillingDate", entitlement.eligibility.getNextBillingDate());
        payload.put("companyId", entitlement.eligibility.getCompanyId());
        payload.put("companySubscriptionId", entitlement.eligibility.getCompanySubscriptionId());
        payload.put("message", entitlement.eligibility.getMessage());
        payload.put("reason", entitlement.eligibility.getReason());
        payload.put("sessionDurationMinutes", entitlement.eligibility.getSessionDurationMinutes() != null
                ? entitlement.eligibility.getSessionDurationMinutes()
                : DEFAULT_SESSION_DURATION_MINUTES);

        if (entitlement.subscription != null) {
            payload.put("subscription", entitlement.subscription);
        }

        if (entitlement.companyMember != null) {
            Map<String, Object> sponsorship = new LinkedHashMap<>();
            sponsorship.put("memberId", entitlement.companyMember.getId());
            sponsorship.put("sessionsUsed", entitlement.companyMember.getSessionsUsed());
            sponsorship.put("profileId", entitlement.companyMember.getProfile() != null ? entitlement.companyMember.getProfile().getId() : null);
            payload.put("companySubscription", buildSponsoredCompanySubscriptionPayload(entitlement.companyMember));
            payload.put("corporateSeat", sponsorship);
        }

        if (entitlement.employeeAllocation != null) {
            Map<String, Object> corporateAllocation = new LinkedHashMap<>();
            corporateAllocation.put("allocationId", entitlement.employeeAllocation.getId());
            corporateAllocation.put("profileId", entitlement.employeeAllocation.getProfile() != null ? entitlement.employeeAllocation.getProfile().getId() : null);
            corporateAllocation.put("allocatedTotal", entitlement.employeeAllocation.getAllocatedTotal());
            corporateAllocation.put("consumedTotal", entitlement.employeeAllocation.getConsumedTotal());
            corporateAllocation.put("availableBalance", entitlement.employeeAllocation.getAvailableBalance());
            payload.put("corporateAllocation", corporateAllocation);
        }

        payload.put("personalCreditsRemaining", entitlement.personalCreditsRemaining);

        return payload;
    }

    private Map<String, Object> buildSponsoredCompanySubscriptionPayload(CompanySubscriptionMember member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        CompanySubscription companySubscription = member.getCompanySubscription();
        if (companySubscription == null) {
            return payload;
        }

        Map<String, Object> companyPayload = new LinkedHashMap<>();
        if (companySubscription.getCompany() != null) {
            companyPayload.put("id", companySubscription.getCompany().getId());
            companyPayload.put("name", companySubscription.getCompany().getName());
        }

        payload.put("id", companySubscription.getId());
        payload.put("company", companyPayload.isEmpty() ? null : companyPayload);
        payload.put("plan", companySubscription.getPlan());
        payload.put("seatsPurchased", companySubscription.getSeatsPurchased());
        payload.put("status", companySubscription.getStatus());
        payload.put("billingInterval", companySubscription.getBillingInterval());
        payload.put("startDate", companySubscription.getStartDate());
        payload.put("endDate", companySubscription.getEndDate());
        payload.put("currentPeriodStart", companySubscription.getCurrentPeriodStart());
        payload.put("currentPeriodEnd", companySubscription.getCurrentPeriodEnd());
        payload.put("autoRenew", companySubscription.getAutoRenew());
        payload.put("createdByUserId", companySubscription.getCreatedByUserId());
        payload.put("createdAt", companySubscription.getCreatedAt());
        payload.put("updatedAt", companySubscription.getUpdatedAt());
        return payload;
    }

    private static final class ResolvedEntitlement {
        private final SessionBookingEligibility eligibility;
        private final Subscription subscription;
        private final CompanySubscriptionMember companyMember;
        @SuppressWarnings("unused")
        private final SubscriptionPlan plan;
        @SuppressWarnings("unused")
        private final int remainingSessions;
        @SuppressWarnings("unused")
        private final int addonSessionsRemaining;
        private final EmployeeSessionAllocation employeeAllocation;
        private final int personalCreditsRemaining;

        private ResolvedEntitlement(SessionBookingEligibility eligibility,
                                    Subscription subscription,
                                    CompanySubscriptionMember companyMember,
                                    SubscriptionPlan plan,
                                    int remainingSessions,
                                    int addonSessionsRemaining,
                                    EmployeeSessionAllocation employeeAllocation) {
            this(eligibility, subscription, companyMember, plan, remainingSessions, addonSessionsRemaining, employeeAllocation, 0);
        }

        private ResolvedEntitlement(SessionBookingEligibility eligibility,
                                    Subscription subscription,
                                    CompanySubscriptionMember companyMember,
                                    SubscriptionPlan plan,
                                    int remainingSessions,
                                    int addonSessionsRemaining,
                                    EmployeeSessionAllocation employeeAllocation,
                                    int personalCreditsRemaining) {
            this.eligibility = eligibility;
            this.subscription = subscription;
            this.companyMember = companyMember;
            this.plan = plan;
            this.remainingSessions = remainingSessions;
            this.addonSessionsRemaining = addonSessionsRemaining;
            this.employeeAllocation = employeeAllocation;
            this.personalCreditsRemaining = personalCreditsRemaining;
        }
    }

    /**
     * Check if user can book a session (has available sessions in subscription or add-ons)
     * @deprecated Use checkSessionBookingEligibility instead for more detailed information
     */
    @Deprecated
    @Transactional(readOnly = true)
    public boolean canBookSession(UUID userId) {
        return checkSessionBookingEligibility(userId).isCanBook();
    }

    /**
     * Get recommended subscription plans that include MENTOR_SESSION feature for 1:1 mentor bookings
     *
     * This method specifically filters plans to ensure they:
     * 1. Are active and available for purchase
     * 2. Include the MENTOR_SESSION feature type (one-on-one mentor sessions)
     * 3. Have the feature enabled and available (not just present but inactive)
     * 4. Are not the user's current plan (if currentPlanId is provided)
     *
     * Returns plans ordered by display order, showing the most recommended plans first
     *
     * @param currentPlanId The user's current plan ID to exclude from recommendations, or null
     * @return List of recommended plans that allow one-on-one mentor bookings
     */
    @Transactional(readOnly = true)
    public List<RecommendedPlanDto> getRecommendedPlansWithMentorSessions(UUID currentPlanId) {
        List<SubscriptionPlan> allPlans = subscriptionPlanRepository.findByIsActiveTrueOrderByDisplayOrderAsc();

        log.info("Finding recommended plans with MENTOR_SESSION feature from {} active plans. Current plan ID to exclude: {}",
                allPlans.size(), currentPlanId);

        List<RecommendedPlanDto> recommendedPlans = allPlans.stream()
            .filter(SubscriptionPlan::supportsIndividualPurchases)
            .filter(this::isPublicMenteeSessionPackage)
            // Filter to ONLY plans that have MENTOR_SESSION feature enabled and available
            // This ensures we only recommend plans that allow one-on-one mentor bookings
            .filter(plan -> {
                log.debug("Evaluating plan '{}' for MENTOR_SESSION feature. Plan has {} features.",
                        plan.getName(), plan.getPlanFeatures().size());

                boolean hasMentorSessions = plan.getPlanFeatures().stream()
                    .anyMatch(pf -> {
                        boolean isCorrectType = pf.getFeature().getType() == Feature.FeatureType.MENTOR_SESSION;
                        boolean isEnabled = pf.getEnabled();
                        boolean isAvailable = pf.isAvailable();

                        log.debug("  Feature '{}' (type: {}): enabled={}, available={}, matches={}",
                                pf.getFeature().getName(),
                                pf.getFeature().getType(),
                                isEnabled,
                                isAvailable,
                                isCorrectType && isEnabled && isAvailable);

                        return isCorrectType && isEnabled && isAvailable;
                    });

                if (hasMentorSessions) {
                    log.info("✓ Plan '{}' includes MENTOR_SESSION feature and WILL be recommended", plan.getName());
                } else {
                    log.info("✗ Plan '{}' does NOT include MENTOR_SESSION feature or it's disabled/unavailable - NOT recommending", plan.getName());
                }

                return hasMentorSessions;
            })
            // Exclude current plan if provided (no point recommending what they already have)
            .filter(plan -> currentPlanId == null || !plan.getId().equals(currentPlanId))
            // Convert to DTO with essential information for frontend
            .map(plan -> RecommendedPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .code(plan.getCode())
                .description(plan.getDescription())
                .cost(plan.getCost())
                .currency(plan.getCurrency())
                .sessionsPerPeriod(plan.getSessionsPerPeriod())
                .displayOrder(plan.getDisplayOrder())
                .features(plan.getFeatures())
                .billingType(plan.getBillingType() != null ? plan.getBillingType().name() : null)
                .build())
            .collect(Collectors.toList());

        log.info("Found {} recommended plans with one-on-one mentor session feature: {}",
                recommendedPlans.size(),
                recommendedPlans.stream().map(RecommendedPlanDto::getName).collect(Collectors.joining(", ")));

        if (recommendedPlans.isEmpty()) {
            log.warn("WARNING: No plans with MENTOR_SESSION feature found! Please check your subscription plan configuration in the database.");
        }

        return recommendedPlans;
    }

    private boolean isPublicMenteeSessionPackage(SubscriptionPlan plan) {
        if (plan == null || plan.getCode() == null) {
            return false;
        }

        return PUBLIC_MENTEE_SESSION_PACKAGE_CODES.contains(plan.getCode().trim().toUpperCase());
    }

    /**
     * Check if user has maxed out their subscription
     */
    @Transactional(readOnly = true)
    public boolean hasMaxedOutSubscription(UUID userId) {
        return !canBookSession(userId);
    }

    /**
     * Consume a session from user's subscription or add-ons
     */
    public void consumeSession(UUID userId) {
        consumeSessionInternal(userId, false);
    }

    /**
     * Consume a session and return the exact individual entitlement used by a booking.
     */
    public SessionConsumptionResult consumeSessionForBooking(UUID userId) {
        return consumeSessionInternal(userId, true);
    }

    private SessionConsumptionResult consumeSessionInternal(UUID userId, boolean requireTrackableIndividualResult) {
        Optional<ResolvedEntitlement> effectiveEntitlementOpt = resolveEffectiveEntitlement(userId);
        if (effectiveEntitlementOpt.isEmpty()) {
            throw new IllegalStateException("No active subscription found for user: " + userId);
        }

        ResolvedEntitlement entitlement = effectiveEntitlementOpt.get();
        if (!entitlement.eligibility.isCanBook()) {
            throw new IllegalStateException("No available sessions for user: " + userId);
        }

        if (entitlement.employeeAllocation != null
                && entitlement.eligibility.getSubscriptionSource() == SessionBookingEligibility.SubscriptionSource.CORPORATE) {
            if (requireTrackableIndividualResult) {
                throw new IllegalStateException("Corporate allocations are consumed by the session booking service");
            }
            employeeSessionAllocationService.consumeBooking(entitlement.employeeAllocation.getId(), userId);
            log.info("Consumed corporate session allocation for user {} via allocation {}", userId, entitlement.employeeAllocation.getId());
            return SessionConsumptionResult.corporateAllocation();
        }

        if (entitlement.companyMember != null
                && entitlement.eligibility.getSubscriptionSource() == SessionBookingEligibility.SubscriptionSource.CORPORATE) {
            CompanySubscriptionMember member = entitlement.companyMember;
            if (member.getCompanySubscription() != null
                    && member.getCompanySubscription().getPlan() != null
                    && member.getCompanySubscription().getPlan().isUnlimited()) {
                log.info("User {} has unlimited corporate sessions, not consuming seat balance", userId);
                return SessionConsumptionResult.corporateSubscription();
            }

            companySubscriptionService.consumeMemberSession(member.getId());
            log.info("Consumed corporate session for user {} via company subscription {}", userId,
                    member.getCompanySubscription() != null ? member.getCompanySubscription().getId() : null);
            return SessionConsumptionResult.corporateSubscription();
        }

        if (entitlement.eligibility.getSubscriptionSource() == SessionBookingEligibility.SubscriptionSource.PERSONAL_CREDIT) {
            if (requireTrackableIndividualResult) {
                throw new IllegalStateException("Personal credits are consumed by the session booking service");
            }
            personalSessionCreditService.consumeNextCredit(userId, null);
            log.info("Consumed personal session credit for user {}", userId);
            return SessionConsumptionResult.personalCredit();
        }

        return consumeIndividualSession(userId, entitlement.subscription);
    }

    private SessionConsumptionResult consumeIndividualSession(UUID userId, Subscription subscription) {
        if (subscription == null) {
            throw new IllegalStateException("No active individual subscription found for user: " + userId);
        }

        if (subscription.getPlan() != null && subscription.getPlan().isUnlimited()) {
            log.info("User {} has unlimited individual plan, not consuming session", userId);
            return SessionConsumptionResult.individualSubscription(subscription.getId());
        }

        if (subscription.hasAvailableSessions()) {
            subscription.incrementSessionsUsed();
            subscriptionRepository.save(subscription);
            log.info("Consumed subscription session for user {}. Sessions used: {}/{}",
                    userId, subscription.getSessionsUsed(), subscription.getSessionsPerMonth());
            return SessionConsumptionResult.individualSubscription(subscription.getId());
        }

        List<SubscriptionAddon> addons = addonRepository.findActiveAddonsWithRemaining(
                subscription.getId(),
                LocalDateTime.now()
        );

        if (addons.isEmpty()) {
            throw new IllegalStateException("No available sessions (subscription or add-ons) for user: " + userId);
        }

        SubscriptionAddon addon = addons.get(0);
        addon.consumeUnit();
        addonRepository.save(addon);

        log.info("Consumed add-on session for user {}. Add-on used: {}/{} (addon: {})",
                userId, addon.getUsed(), addon.getQuantity(), addon.getId());
        return SessionConsumptionResult.subscriptionAddon(subscription.getId(), addon.getId());
    }

    /**
     * Return the individual subscription/add-on entitlement consumed by a mentor-declined booking.
     */
    public void returnConsumedSessionForDeclinedBooking(UUID userId, UUID subscriptionId, UUID addonId) {
        if (addonId != null) {
            returnTrackedAddonConsumption(userId, addonId);
            return;
        }

        if (subscriptionId != null) {
            returnTrackedSubscriptionConsumption(userId, subscriptionId);
            return;
        }

        returnFallbackIndividualConsumption(userId);
    }

    private void returnTrackedAddonConsumption(UUID userId, UUID addonId) {
        SubscriptionAddon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> new IllegalStateException("Consumed add-on not found: " + addonId));

        assertAddonBelongsToUser(addon, userId);
        addon.restoreUnit();
        addonRepository.save(addon);

        log.info("Returned consumed add-on session {} for user {}", addonId, userId);
    }

    private void returnTrackedSubscriptionConsumption(UUID userId, UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("Consumed subscription not found: " + subscriptionId));

        assertSubscriptionBelongsToUser(subscription, userId);
        subscription.decrementSessionsUsed();
        subscriptionRepository.save(subscription);

        log.info("Returned consumed subscription session {} for user {}", subscriptionId, userId);
    }

    private void returnFallbackIndividualConsumption(UUID userId) {
        Subscription subscription = getActiveIndividualSubscription(userId)
                .orElseThrow(() -> new IllegalStateException("No active individual subscription found for user: " + userId));

        List<SubscriptionAddon> restorableAddons = addonRepository.findRestorableSessionAddons(subscription.getId());
        if (!restorableAddons.isEmpty()) {
            SubscriptionAddon addon = restorableAddons.get(0);
            addon.restoreUnit();
            addonRepository.save(addon);
            log.info("Returned fallback consumed add-on session {} for user {}", addon.getId(), userId);
            return;
        }

        subscription.decrementSessionsUsed();
        subscriptionRepository.save(subscription);
        log.info("Returned fallback consumed subscription session {} for user {}", subscription.getId(), userId);
    }

    private void assertAddonBelongsToUser(SubscriptionAddon addon, UUID userId) {
        if (addon.getSubscription() == null) {
            throw new IllegalStateException("Consumed add-on is not linked to a subscription");
        }
        assertSubscriptionBelongsToUser(addon.getSubscription(), userId);
    }

    private void assertSubscriptionBelongsToUser(Subscription subscription, UUID userId) {
        if (subscription.getUserId() == null || !subscription.getUserId().equals(userId)) {
            throw new IllegalStateException("Consumed entitlement does not belong to user: " + userId);
        }
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
        if (!plan.supportsIndividualPurchases()) {
            return ApiResponse.error("Selected plan is only available for corporate purchase");
        }

        final BillingInterval billingInterval;
        try {
            billingInterval = validateBillingInterval(plan, request.getBillingInterval());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ApiResponse.error(ex.getMessage());
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
                // Convert plan cost to user's preferred currency
                String targetCurrency = (request.getCurrency() != null && !request.getCurrency().trim().isEmpty())
                                       ? request.getCurrency().toUpperCase()
                                       : currencyService.getDefaultCurrency();
                BigDecimal planAmount = resolvePlanPrice(plan, billingInterval);

                java.math.BigDecimal convertedCost;
                try {
                    convertedCost = currencyService.convertToUserCurrency(planAmount, targetCurrency);
                    log.info("Converted subscription cost from {} {} to {} {} for {} billing",
                            plan.getCurrency(), planAmount, targetCurrency, convertedCost, formatBillingInterval(billingInterval));
                } catch (Exception e) {
                    log.error("Failed to convert currency, using default: {}", e.getMessage());
                    convertedCost = currencyService.convertFromUSDToDefault(planAmount);
                    targetCurrency = currencyService.getDefaultCurrency();
                }

                // Initiate payment via Mpesa
                Payment payment = mpesaService.initiateSTKPush(
                    request.getUserId(),
                    null, // sessionId - not applicable
                    subscription.getId(), // subscriptionId
                    Payment.PaymentType.SUBSCRIPTION,
                    convertedCost,
                    request.getPhoneNumber(),
                    String.format("Subscription payment for %s (%s billing, %s %s)",
                                  plan.getName(), formatBillingInterval(billingInterval), targetCurrency, convertedCost)
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
        BillingInterval billingInterval = validateBillingInterval(plan, request.getBillingInterval());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(resolvePlanDurationMonths(plan, billingInterval));

        Subscription subscription = new Subscription();
        subscription.setUserId(request.getUserId());
        subscription.setPlan(plan);
        subscription.setBillingInterval(billingInterval);
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
        BillingInterval billingInterval = validateBillingInterval(plan, request.getBillingInterval());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(resolvePlanDurationMonths(plan, billingInterval));

        Subscription subscription = new Subscription();
        subscription.setUserId(request.getUserId());
        subscription.setPlan(plan);
        subscription.setBillingInterval(billingInterval);
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
        BillingInterval billingInterval = resolveBillingInterval(subscription);
        int durationMonths = resolvePlanDurationMonths(subscription.getPlan(), billingInterval);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(now);
        subscription.setEndDate(now.plusMonths(durationMonths));
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(durationMonths));

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
     * Upgrade user's subscription with proration
     */
    public ApiResponse<SubscriptionUpgradeResponse> upgradeSubscription(UpgradeSubscriptionRequest request) {
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
        if (!newPlan.supportsIndividualPurchases()) {
            return ApiResponse.error("Selected plan is only available for corporate purchase");
        }
        if (!newPlan.supportsIndividualPurchases()) {
            return ApiResponse.error("Selected plan is only available for corporate purchase");
        }

        Subscription subscription = subscriptionOpt.get();
        SubscriptionPlan currentPlan = subscription.getPlan();
        BillingInterval currentInterval = resolveBillingInterval(subscription);
        final BillingInterval targetInterval;
        try {
            targetInterval = validateBillingInterval(newPlan, request.getBillingInterval());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ApiResponse.error(ex.getMessage());
        }

        if (currentPlan != null
                && newPlan.getId() != null
                && newPlan.getId().equals(currentPlan.getId())
                && currentInterval == targetInterval) {
            return ApiResponse.error("User is already on the selected billing option");
        }

        if (currentPlan != null
                && newPlan.getDisplayOrder() != null
                && currentPlan.getDisplayOrder() != null
                && newPlan.getDisplayOrder() < currentPlan.getDisplayOrder()) {
            return ApiResponse.error("Cannot switch to a lower plan from the upgrade flow");
        }

        // Validate phone number
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
            return ApiResponse.error("Phone number is required for upgrade payment");
        }

        // Calculate prorated amount
        java.math.BigDecimal proratedAmount = calculatePlanChangeAmount(
                subscription,
                currentPlan,
                currentInterval,
                newPlan,
                targetInterval
        );

        log.info("Calculated prorated amount for upgrade: {} {}", proratedAmount, newPlan.getCurrency());

        // If prorated amount is zero or negative (shouldn't happen due to upgrade check), handle gracefully
        if (proratedAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ApiResponse.error("Invalid prorated amount calculated. Please contact support.");
        }

        try {
            // Initiate payment for prorated amount
            Payment payment = mpesaService.initiateSTKPush(
                request.getUserId(),
                null, // sessionId - not applicable
                subscription.getId(),
                Payment.PaymentType.UPGRADE,
                proratedAmount,
                request.getPhoneNumber(),
                String.format("Upgrade to %s plan (%s billing)", newPlan.getName(), formatBillingInterval(targetInterval))
            );

            log.info("Payment initiated for subscription upgrade {} with payment ID {}",
                subscription.getId(), payment.getId());

            // Store the target plan ID in payment metadata for callback processing
            payment.setMetadata(String.format(
                    "{\"targetPlanId\":\"%s\",\"billingInterval\":\"%s\"}",
                    newPlan.getId(),
                    targetInterval.name()
            ));

            // Mark subscription as pending upgrade (we'll update it upon payment confirmation)
            // For now, we'll keep the subscription as is and let the payment callback handle the upgrade

            SubscriptionUpgradeResponse response = new SubscriptionUpgradeResponse();
            response.setSubscription(subscription);
            response.setPayment(payment);
            response.setProratedAmount(proratedAmount);
            response.setNewPlan(newPlan);
            response.setMessage("Payment initiated. Subscription will be upgraded after payment confirmation.");

            return ApiResponse.success("Payment initiated for upgrade. Please complete payment on your phone.", response);

        } catch (Exception e) {
            log.error("Failed to initiate payment for subscription upgrade {}: {}",
                subscription.getId(), e.getMessage(), e);

            return ApiResponse.error("Failed to initiate payment: " + e.getMessage());
        }
    }

    /**
     * Calculate prorated upgrade amount
     * Formula: (Days remaining / Total days in period) * Current plan cost + New plan cost - Current plan cost
     * Simplified: New plan cost - (Days used / Total days) * Current plan cost
     */
    private java.math.BigDecimal calculatePlanChangeAmount(
            Subscription subscription,
            SubscriptionPlan currentPlan,
            BillingInterval currentInterval,
            SubscriptionPlan newPlan,
            BillingInterval newInterval) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = subscription.getCurrentPeriodStart();
        LocalDateTime periodEnd = subscription.getCurrentPeriodEnd();

        // Calculate total days in current period
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);

        // Calculate days remaining in current period
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, periodEnd);

        // Ensure we don't have negative values
        if (daysRemaining < 0) daysRemaining = 0;
        if (totalDays <= 0) totalDays = 1; // Prevent division by zero

        // Calculate unused portion of current plan
        java.math.BigDecimal unusedRatio = java.math.BigDecimal.valueOf(daysRemaining)
            .divide(java.math.BigDecimal.valueOf(totalDays), 4, java.math.RoundingMode.HALF_UP);

        java.math.BigDecimal currentPlanPrice = resolvePlanPrice(currentPlan, currentInterval);
        java.math.BigDecimal targetPlanPrice = resolvePlanPrice(newPlan, newInterval);

        java.math.BigDecimal unusedCurrentPlanValue = currentPlanPrice
            .multiply(unusedRatio);
        java.math.BigDecimal proratedAmount = targetPlanPrice.subtract(unusedCurrentPlanValue);

        // Ensure the amount is positive
        if (proratedAmount.compareTo(java.math.BigDecimal.ZERO) < 0) {
            proratedAmount = java.math.BigDecimal.ZERO;
        }

        log.info("Proration calculation: totalDays={}, daysRemaining={}, unusedRatio={}, " +
                 "currentPlanCost={}, newPlanCost={}, unusedCurrentValue={}, proratedAmount={}, currentInterval={}, newInterval={}",
                 totalDays, daysRemaining, unusedRatio,
                 currentPlanPrice, targetPlanPrice, unusedCurrentPlanValue, proratedAmount, currentInterval, newInterval);

        return proratedAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Complete subscription upgrade after payment confirmation
     * This should be called from the payment callback handler
     */
    public ApiResponse<Subscription> completeSubscriptionUpgrade(UUID subscriptionId,
                                                                UUID newPlanId,
                                                                BillingInterval billingInterval) {
        log.info("Completing subscription upgrade for subscription {} to plan {} with {} billing",
                subscriptionId, newPlanId, billingInterval);

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("Subscription not found");
        }

        Optional<SubscriptionPlan> newPlanOpt = subscriptionPlanRepository.findById(newPlanId);
        if (newPlanOpt.isEmpty()) {
            return ApiResponse.error("New subscription plan not found");
        }

        Subscription subscription = subscriptionOpt.get();
        SubscriptionPlan newPlan = newPlanOpt.get();

        BillingInterval resolvedInterval = validateBillingInterval(newPlan, billingInterval);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextPeriodEnd = now.plusMonths(resolvePlanDurationMonths(newPlan, resolvedInterval));

        subscription.setPlan(newPlan);
        subscription.setBillingInterval(resolvedInterval);
        subscription.setSessionsPerMonth(newPlan.getSessionsPerPeriod());
        // Reset sessions used when upgrading
        subscription.setSessionsUsed(0);
        subscription.setIsTrial(false);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(nextPeriodEnd);
        subscription.setEndDate(nextPeriodEnd);
        if (subscription.getStartDate() == null) {
            subscription.setStartDate(now);
        }

        subscription = subscriptionRepository.save(subscription);

        log.info("Completed upgrade for subscription {} to plan {}", subscriptionId, newPlan.getName());

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
     * Update auto-renew preference for the user's current subscription.
     */
    public ApiResponse<Subscription> updateAutoRenewPreference(UUID userId, Boolean autoRenew) {
        if (userId == null) {
            return ApiResponse.error("userId is required");
        }
        if (autoRenew == null) {
            return ApiResponse.error("autoRenew is required");
        }

        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("No active subscription found for user");
        }

        Subscription subscription = subscriptionOpt.get();
        subscription.setAutoRenew(autoRenew);
        if (!Boolean.TRUE.equals(autoRenew)) {
            subscription.setAutoRenewLastFailureReason(null);
        }

        subscription = subscriptionRepository.save(subscription);
        String message = autoRenew
                ? "Auto-renew enabled successfully"
                : "Auto-renew disabled successfully";
        return ApiResponse.success(message, subscription);
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
     * Apply a successful invoice payment that represents one renewal cycle
     * for an existing subscription.
     */
    public ApiResponse<Subscription> applyRenewalInvoicePayment(UUID userId,
                                                                UUID subscriptionId,
                                                                String renewalMode) {
        if (subscriptionId == null) {
            return ApiResponse.error("subscriptionId is required");
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(subscriptionId);
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("Subscription not found");
        }

        Subscription subscription = subscriptionOpt.get();
        if (userId != null && subscription.getUserId() != null && !subscription.getUserId().equals(userId)) {
            log.warn("Renewal invoice user mismatch. Expected {}, got {} for subscription {}",
                    subscription.getUserId(), userId, subscriptionId);
        }

        subscription.applyPaidRenewal();
        subscription.setAutoRenewLastChargeAt(LocalDateTime.now());
        subscription.setAutoRenewLastFailureReason(null);
        subscription = subscriptionRepository.save(subscription);

        log.info("Applied paid renewal for subscription {} via invoice flow (mode={})",
                subscriptionId, renewalMode);
        return ApiResponse.success("Subscription renewed successfully", subscription);
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
            if (Boolean.TRUE.equals(subscription.getAutoRenew())) {
                subscription.setStatus(Subscription.SubscriptionStatus.SUSPENDED);
                subscription.setAutoRenewLastFailureReason("Subscription expired before automatic renewal payment was processed");
            } else {
                subscription.setStatus(Subscription.SubscriptionStatus.EXPIRED);
            }
            subscriptionRepository.save(subscription);
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
        LocalDateTime now = LocalDateTime.now();
        int durationMonths = resolvePlanDurationMonths(subscription.getPlan(), resolveBillingInterval(subscription));
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(durationMonths));
        subscription.setEndDate(subscription.getCurrentPeriodEnd());

        subscriptionRepository.save(subscription);

        log.info("Reset billing period for subscription {}", subscriptionId);
    }

    /**
     * Get remaining sessions count for a user
     */
    @Transactional(readOnly = true)
    public int getRemainingSessionsCount(UUID userId) {
        Optional<ResolvedEntitlement> effectiveEntitlementOpt = resolveEffectiveEntitlement(userId);
        if (effectiveEntitlementOpt.isEmpty()) {
            return 0;
        }

        SessionBookingEligibility eligibility = effectiveEntitlementOpt.get().eligibility;
        Integer subscriptionSessions = eligibility.getSessionsRemaining();
        Integer addonSessions = eligibility.getAddonSessionsRemaining();

        if (subscriptionSessions != null && subscriptionSessions == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return Math.max(0, (subscriptionSessions != null ? subscriptionSessions : 0)
                + (addonSessions != null ? addonSessions : 0));
    }

    /**
     * Get all subscription plans
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findAllByOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans(String audience) {
        return filterPlansByAudience(getAllPlans(), audience);
    }

    /**
     * Get all active subscription plans
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        return subscriptionPlanRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans(String audience) {
        return filterPlansByAudience(getActivePlans(), audience);
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

    private List<SubscriptionPlan> filterPlansByAudience(List<SubscriptionPlan> plans, String audience) {
        if (audience == null || audience.isBlank()) {
            return plans;
        }

        String normalizedAudience = audience.trim().toUpperCase();
        return plans.stream()
                .filter(plan -> switch (normalizedAudience) {
                    case "INDIVIDUAL" -> plan.supportsIndividualPurchases();
                    case "CORPORATE" -> plan.supportsCorporatePurchases();
                    case "BOTH" -> plan.getPlanAudience() == SubscriptionPlan.PlanAudience.BOTH;
                    default -> true;
                })
                .toList();
    }

    private SubscriptionPlan.PlanAudience mapPlanAudience(CreateSubscriptionPlanRequest.SubscriptionPlanAudience audience) {
        if (audience == null) {
            return SubscriptionPlan.PlanAudience.INDIVIDUAL;
        }
        return SubscriptionPlan.PlanAudience.valueOf(audience.name());
    }

    private void applyCorporateSeatSettings(SubscriptionPlan plan,
                                            Integer minSeats,
                                            Integer defaultSeats,
                                            Integer maxSeats) {
        int resolvedMin = minSeats != null ? minSeats : 1;
        int resolvedDefault = defaultSeats != null ? defaultSeats : resolvedMin;

        if (resolvedMin < 1) {
            throw new IllegalArgumentException("minSeats must be at least 1");
        }
        if (resolvedDefault < resolvedMin) {
            throw new IllegalArgumentException("defaultSeats must be greater than or equal to minSeats");
        }
        if (maxSeats != null && maxSeats < resolvedDefault) {
            throw new IllegalArgumentException("maxSeats must be greater than or equal to defaultSeats");
        }

        plan.setMinSeats(resolvedMin);
        plan.setDefaultSeats(resolvedDefault);
        plan.setMaxSeats(maxSeats);
    }

    private BillingInterval resolveBillingInterval(BillingInterval billingInterval) {
        return billingInterval != null ? billingInterval : BillingInterval.MONTHLY;
    }

    private BillingInterval resolveBillingInterval(Subscription subscription) {
        if (subscription == null || subscription.getBillingInterval() == null) {
            return BillingInterval.MONTHLY;
        }
        return subscription.getBillingInterval();
    }

    private BillingInterval validateBillingInterval(SubscriptionPlan plan, BillingInterval billingInterval) {
        BillingInterval resolvedInterval = resolveBillingInterval(billingInterval);
        if (plan == null) {
            throw new IllegalArgumentException("Subscription plan not found");
        }
        if (!plan.supportsBillingInterval(resolvedInterval)) {
            throw new IllegalStateException(resolvedInterval == BillingInterval.ANNUAL
                    ? "Annual billing is not configured for the selected plan"
                    : "Selected billing interval is not available for the plan");
        }
        return resolvedInterval;
    }

    private BigDecimal resolvePlanPrice(SubscriptionPlan plan, BillingInterval billingInterval) {
        BillingInterval resolvedInterval = validateBillingInterval(plan, billingInterval);
        return plan.resolvePriceForInterval(resolvedInterval).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private int resolvePlanDurationMonths(SubscriptionPlan plan, BillingInterval billingInterval) {
        BillingInterval resolvedInterval = resolveBillingInterval(billingInterval);
        return plan != null ? plan.resolveDurationMonthsForInterval(resolvedInterval) : 1;
    }

    private String formatBillingInterval(BillingInterval billingInterval) {
        return resolveBillingInterval(billingInterval) == BillingInterval.ANNUAL ? "annual" : "monthly";
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
        plan.setYearlyCost(request.getYearlyCost());
        plan.setIsActive(request.getIsActive());
        plan.setDisplayOrder(request.getDisplayOrder());
        plan.setFeatures(request.getFeatures());
        plan.setPlanAudience(mapPlanAudience(request.getPlanAudience()));
        applyCorporateSeatSettings(plan, request.getMinSeats(), request.getDefaultSeats(), request.getMaxSeats());

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

        if (request.getYearlyCost() != null) {
            existingPlan.setYearlyCost(request.getYearlyCost());
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
        if (request.getPlanAudience() != null) {
            existingPlan.setPlanAudience(mapPlanAudience(request.getPlanAudience()));
        }
        if (request.getMinSeats() != null || request.getDefaultSeats() != null || request.getMaxSeats() != null) {
            applyCorporateSeatSettings(
                    existingPlan,
                    request.getMinSeats() != null ? request.getMinSeats() : existingPlan.getMinSeats(),
                    request.getDefaultSeats() != null ? request.getDefaultSeats() : existingPlan.getDefaultSeats(),
                    request.getMaxSeats() != null ? request.getMaxSeats() : existingPlan.getMaxSeats()
            );
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

    // ========== FEATURE-BASED ACCESS METHODS ==========

    /**
     * Check if user can access a specific feature
     */
    @Transactional(readOnly = true)
    public boolean canAccessFeature(UUID userId, String featureCode) {
        Optional<ResolvedEntitlement> entitlementOpt = resolveEffectiveEntitlement(userId);
        if (entitlementOpt.isEmpty() || entitlementOpt.get().plan == null) {
            log.debug("User {} has no active subscription for feature {}", userId, featureCode);
            return false;
        }

        SubscriptionPlan plan = entitlementOpt.get().plan;

        boolean hasAccess = plan.hasFeature(featureCode);
        log.debug("User {} {} access to feature {}", userId, hasAccess ? "has" : "does not have", featureCode);

        return hasAccess;
    }

    /**
     * Get all features available to a user with their limits
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserFeatures(UUID userId) {
        Optional<ResolvedEntitlement> entitlementOpt = resolveEffectiveEntitlement(userId);
        if (entitlementOpt.isEmpty() || entitlementOpt.get().plan == null) {
            return Map.of(
                "tier", "none",
                "features", List.of(),
                "sessionsRemaining", 0,
                "addonSessions", 0
            );
        }

        ResolvedEntitlement entitlement = entitlementOpt.get();
        SubscriptionPlan plan = entitlement.plan;

        List<Map<String, Object>> features = plan.getPlanFeatures().stream()
            .filter(PlanFeature::getEnabled)
            .filter(PlanFeature::isAvailable)
            .map(pf -> {
                Map<String, Object> featureMap = new HashMap<>();
                featureMap.put("code", pf.getFeature().getCode());
                featureMap.put("name", pf.getFeature().getName());
                featureMap.put("description", pf.getFeature().getDescription());
                featureMap.put("type", pf.getFeature().getType().toString());
                featureMap.put("limit", pf.getLimitValue());
                featureMap.put("unlimited", pf.isUnlimited());
                return featureMap;
            })
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("tier", plan.getName());
        result.put("planCode", plan.getCode());
        result.put("features", features);
        result.put("sessionsRemaining", getRemainingSessionsCount(userId));
        result.put("addonSessions", entitlement.subscription != null ? getAddonSessionsRemaining(entitlement.subscription.getId()) : 0);
        result.put("allowsAddons", plan.getAllowsAddons());
        result.put("addonSessionCost", plan.getAddonSessionCost());
        result.put("subscriptionSource", entitlement.eligibility.getSubscriptionSource());
        result.put("companyId", entitlement.eligibility.getCompanyId());
        result.put("companySubscriptionId", entitlement.eligibility.getCompanySubscriptionId());

        return result;
    }

    /**
     * Get feature limit for a user
     */
    @Transactional(readOnly = true)
    public Integer getUserFeatureLimit(UUID userId, String featureCode) {
        Optional<ResolvedEntitlement> entitlementOpt = resolveEffectiveEntitlement(userId);
        if (entitlementOpt.isEmpty() || entitlementOpt.get().plan == null) {
            return 0;
        }

        return entitlementOpt.get().plan.getFeatureLimit(featureCode);
    }

    // ========== ADD-ON MANAGEMENT METHODS ==========

    /**
     * Get remaining add-on sessions for a subscription
     */
    @Transactional(readOnly = true)
    public int getAddonSessionsRemaining(UUID subscriptionId) {
        Integer remaining = addonRepository.getTotalRemainingUnits(
            subscriptionId,
            "EXTRA_SESSION",
            LocalDateTime.now()
        );
        return remaining != null ? remaining : 0;
    }

    /**
     * Purchase add-on sessions
     */
    public ApiResponse<PurchaseAddonResponse> purchaseAddonSessions(String userId, int quantity, String phoneNumber, String currency) {
        log.info("User {} purchasing {} add-on sessions in currency {}", userId, quantity, currency);

        Optional<Subscription> subscriptionOpt = getActiveSubscription(UUID.fromString(userId));
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("No active subscription found");
        }

        Subscription subscription = subscriptionOpt.get();
        SubscriptionPlan plan = subscription.getPlan();

        if (!plan.getAllowsAddons()) {
            return ApiResponse.error("This subscription plan does not allow add-ons");
        }

        if (plan.getAddonSessionCost() == null) {
            return ApiResponse.error("Add-on pricing not configured for this plan");
        }

        if (quantity <= 0 || quantity > 50) {
            return ApiResponse.error("Invalid quantity. Must be between 1 and 50");
        }

        // Convert addon cost to user's preferred currency
        String targetCurrency = (currency != null && !currency.trim().isEmpty())
                               ? currency.toUpperCase()
                               : currencyService.getDefaultCurrency();

        java.math.BigDecimal addonCostPerSession;
        try {
            addonCostPerSession = currencyService.convertToUserCurrency(
                plan.getAddonSessionCost(),
                targetCurrency
            );
            log.info("Converted addon session cost from USD {} to {} {}",
                    plan.getAddonSessionCost(), addonCostPerSession, targetCurrency);
        } catch (Exception e) {
            log.error("Failed to convert currency, using default: {}", e.getMessage());
            addonCostPerSession = currencyService.convertFromUSDToDefault(plan.getAddonSessionCost());
            targetCurrency = currencyService.getDefaultCurrency();
        }

        java.math.BigDecimal totalCost = addonCostPerSession.multiply(java.math.BigDecimal.valueOf(quantity));

        // Create addon record
        SubscriptionAddon addon = new SubscriptionAddon();
        addon.setSubscription(subscription);
        addon.setAddonType("EXTRA_SESSION");
        addon.setAddonName("Extra 1:1 Sessions");
        addon.setQuantity(quantity);
        addon.setUsed(0);
        addon.setTotalCost(totalCost);
        addon.setCurrency(targetCurrency);
        addon.setStatus(SubscriptionAddon.AddonStatus.ACTIVE);
        // Set expiry to end of subscription period
        addon.setExpiresAt(subscription.getCurrentPeriodEnd());

        addon = addonRepository.save(addon);

        try {
            // Initiate payment via Mpesa
            Payment payment = mpesaService.initiateSTKPush(
                UUID.fromString(userId),
                null,
                subscription.getId(),
                Payment.PaymentType.ADDON,
                totalCost,
                phoneNumber,
                String.format("Purchase %d extra sessions (%s %s)",
                              quantity, targetCurrency, totalCost)
            );

            addon.setPaymentId(payment.getId());
            addon = addonRepository.save(addon);

            log.info("Created addon {} with payment {}", addon.getId(), payment.getId());

            PurchaseAddonResponse response = PurchaseAddonResponse.of(addon, payment);
            return ApiResponse.success("Payment initiated for add-on sessions", response);

        } catch (Exception e) {
            log.error("Failed to initiate payment for addon: {}", e.getMessage(), e);
            // Delete the addon since payment failed
            addonRepository.delete(addon);
            return ApiResponse.error("Failed to initiate payment: " + e.getMessage());
        }
    }

    /**
     * Apply a plan payment that was completed through the unified invoice flow.
     * If user has an active subscription, this upgrades/changes the plan.
     * If user has no active subscription, this creates a new active subscription.
     */
    public ApiResponse<Subscription> applyPlanInvoicePayment(UUID userId, UUID planId, BillingInterval billingInterval) {
        if (userId == null || planId == null) {
            return ApiResponse.error("userId, planId and billingInterval are required");
        }

        Optional<SubscriptionPlan> newPlanOpt = subscriptionPlanRepository.findById(planId);
        if (newPlanOpt.isEmpty()) {
            return ApiResponse.error("Subscription plan not found");
        }

        SubscriptionPlan newPlan = newPlanOpt.get();
        if (!newPlan.getIsActive()) {
            return ApiResponse.error("Subscription plan is not active");
        }
        final BillingInterval resolvedInterval;
        try {
            resolvedInterval = validateBillingInterval(newPlan, billingInterval);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return ApiResponse.error(ex.getMessage());
        }

        Optional<Subscription> activeSubscriptionOpt = getActiveSubscription(userId);
        if (isOneTimePlan(newPlan)) {
            return applyOneTimePlanInvoicePayment(userId, newPlan, resolvedInterval, activeSubscriptionOpt);
        }

        if (activeSubscriptionOpt.isPresent()) {
            Subscription activeSubscription = activeSubscriptionOpt.get();
            if (activeSubscription.getPlan() != null
                    && planId.equals(activeSubscription.getPlan().getId())
                    && resolveBillingInterval(activeSubscription) == resolvedInterval) {
                return ApiResponse.success("User is already on this plan", activeSubscription);
            }
            if (activeSubscription.getPlan() != null
                    && activeSubscription.getPlan().getDisplayOrder() != null
                    && newPlan.getDisplayOrder() != null
                    && newPlan.getDisplayOrder() < activeSubscription.getPlan().getDisplayOrder()) {
                return ApiResponse.error("Selected plan must be equal or higher than current plan");
            }

            return completeSubscriptionUpgrade(activeSubscription.getId(), planId, resolvedInterval);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(resolvePlanDurationMonths(newPlan, resolvedInterval));

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(newPlan);
        subscription.setBillingInterval(resolvedInterval);
        subscription.setSessionsPerMonth(newPlan.getSessionsPerPeriod());
        subscription.setSessionsUsed(0);
        subscription.setStartDate(now);
        subscription.setEndDate(endDate);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(endDate);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(true);
        subscription.setIsTrial(false);

        subscription = subscriptionRepository.save(subscription);
        log.info("Created active subscription {} for user {} from invoice payment", subscription.getId(), userId);
        return ApiResponse.success("Subscription activated successfully", subscription);
    }

    private ApiResponse<Subscription> applyOneTimePlanInvoicePayment(UUID userId,
                                                                     SubscriptionPlan plan,
                                                                     BillingInterval billingInterval,
                                                                     Optional<Subscription> activeSubscriptionOpt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(resolvePlanDurationMonths(plan, billingInterval));

        Subscription subscription = activeSubscriptionOpt.orElseGet(Subscription::new);
        if (subscription.getId() == null) {
            subscription.setUserId(userId);
            if (subscription.getStartDate() == null) {
                subscription.setStartDate(now);
            }
        }

        subscription.setPlan(plan);
        subscription.setBillingInterval(billingInterval);
        subscription.setSessionsPerMonth(plan.getSessionsPerPeriod());
        subscription.setSessionsUsed(0);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(endDate);
        subscription.setEndDate(endDate);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(false);
        subscription.setIsTrial(false);

        subscription = subscriptionRepository.save(subscription);
        log.info("Applied one-time plan {} to subscription {} for user {} from invoice payment",
                plan.getCode(), subscription.getId(), userId);
        return ApiResponse.success("Session package activated successfully", subscription);
    }

    private boolean isOneTimePlan(SubscriptionPlan plan) {
        return plan != null && plan.getBillingType() == SubscriptionPlan.BillingType.ONE_TIME;
    }

    public ApiResponse<Subscription> applyZeroCostPlanChange(UUID userId, UUID planId, BillingInterval billingInterval) {
        final Map<String, Object> quote;
        try {
            quote = quotePlanInvoice(userId, planId, billingInterval);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ApiResponse.error(ex.getMessage());
        }

        boolean requiresPayment = Boolean.TRUE.equals(quote.get("requiresPayment"));
        Object amountValue = quote.get("amount");
        BigDecimal amount = amountValue instanceof BigDecimal
                ? (BigDecimal) amountValue
                : new BigDecimal(String.valueOf(amountValue != null ? amountValue : "0"));

        if (requiresPayment || amount.compareTo(BigDecimal.ZERO) > 0) {
            return ApiResponse.error("Selected plan requires payment before it can be applied");
        }

        Object quotedInterval = quote.get("billingInterval");
        BillingInterval resolvedInterval = quotedInterval instanceof BillingInterval
                ? (BillingInterval) quotedInterval
                : BillingInterval.fromString(String.valueOf(quotedInterval));

        return applyPlanInvoicePayment(userId, planId, resolvedInterval);
    }

    /**
     * Apply add-on sessions paid through the unified invoice flow.
     */
    public ApiResponse<SubscriptionAddon> applyAddonInvoicePayment(UUID userId,
                                                                   int quantity,
                                                                   BigDecimal totalCost,
                                                                   String currency,
                                                                   UUID paymentId) {
        if (userId == null) {
            return ApiResponse.error("userId is required");
        }

        if (quantity <= 0 || quantity > 50) {
            return ApiResponse.error("Invalid quantity. Must be between 1 and 50");
        }

        if (paymentId != null) {
            List<SubscriptionAddon> existing = addonRepository.findByPaymentId(paymentId);
            if (!existing.isEmpty()) {
                return ApiResponse.success("Add-on already applied", existing.get(0));
            }
        }

        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);
        if (subscriptionOpt.isEmpty()) {
            return ApiResponse.error("No active subscription found");
        }

        Subscription subscription = subscriptionOpt.get();
        SubscriptionPlan plan = subscription.getPlan();

        if (plan == null || !Boolean.TRUE.equals(plan.getAllowsAddons())) {
            return ApiResponse.error("This subscription plan does not allow add-ons");
        }

        BigDecimal resolvedTotalCost = totalCost;
        if (resolvedTotalCost == null || resolvedTotalCost.compareTo(BigDecimal.ZERO) <= 0) {
            if (plan.getAddonSessionCost() == null || plan.getAddonSessionCost().compareTo(BigDecimal.ZERO) <= 0) {
                return ApiResponse.error("Add-on pricing not configured for this plan");
            }
            resolvedTotalCost = plan.getAddonSessionCost().multiply(BigDecimal.valueOf(quantity));
        }

        SubscriptionAddon addon = new SubscriptionAddon();
        addon.setSubscription(subscription);
        addon.setAddonType("EXTRA_SESSION");
        addon.setAddonName("Extra 1:1 Sessions");
        addon.setQuantity(quantity);
        addon.setUsed(0);
        addon.setTotalCost(resolvedTotalCost);
        addon.setCurrency((currency == null || currency.isBlank()) ? "KES" : currency.toUpperCase());
        addon.setStatus(SubscriptionAddon.AddonStatus.ACTIVE);
        addon.setExpiresAt(subscription.getCurrentPeriodEnd());
        addon.setPaymentId(paymentId);

        addon = addonRepository.save(addon);
        log.info("Applied invoice add-on {} for user {} (quantity {}, paymentId {})",
                addon.getId(), userId, quantity, paymentId);
        return ApiResponse.success("Add-on sessions applied successfully", addon);
    }

    /**
     * Capture and store reusable CyberSource token/card details on a subscription
     * after a successful card payment callback.
     */
    public void registerAutoRenewCardFromCyberSourceCallback(Payment payment, Map<String, String> callbackData) {
        if (payment == null || callbackData == null || callbackData.isEmpty()) {
            return;
        }

        Optional<Subscription> subscriptionOpt = resolveSubscriptionForRecurringTokenCapture(payment);
        if (subscriptionOpt.isEmpty()) {
            log.info("Skipping recurring card registration: no subscription resolved for payment {}", payment.getId());
            return;
        }

        Subscription subscription = subscriptionOpt.get();

        String customerToken = firstNonBlank(
                callbackData.get("customer_token"),
                callbackData.get("req_customer_token"),
                callbackData.get("customer_id"),
                callbackData.get("req_customer_id")
        );
        String paymentInstrumentId = firstNonBlank(
                callbackData.get("payment_instrument_id"),
                callbackData.get("req_payment_instrument_id")
        );
        String paymentToken = firstNonBlank(
                callbackData.get("payment_token"),
                callbackData.get("req_payment_token")
        );
        String cardType = firstNonBlank(
                callbackData.get("card_type"),
                callbackData.get("req_card_type")
        );
        String maskedCard = firstNonBlank(
                callbackData.get("card_number"),
                callbackData.get("req_card_number")
        );

        if (isBlank(customerToken) && isBlank(paymentInstrumentId) && isBlank(paymentToken)) {
            log.info("No recurring token fields in CyberSource callback for payment {}. " +
                    "Customer token/instrument id not captured.", payment.getId());
            return;
        }

        subscription.setAutoRenewCustomerToken(emptyToNull(customerToken));
        subscription.setAutoRenewPaymentInstrumentId(emptyToNull(paymentInstrumentId));
        subscription.setAutoRenewPaymentToken(emptyToNull(paymentToken));
        subscription.setAutoRenewCardType(emptyToNull(cardType));
        subscription.setAutoRenewCardLastFour(extractLastFour(maskedCard));
        boolean hasReusableInstrument = !isBlank(customerToken) && !isBlank(paymentInstrumentId);
        subscription.setAutoRenewCardOnFile(hasReusableInstrument);
        subscription.setAutoRenewTokenizedAt(LocalDateTime.now());
        subscription.setAutoRenewLastFailureReason(null);

        subscriptionRepository.save(subscription);
        log.info("Stored recurring payment token data for subscription {} (payment {}, hasCustomerToken={}, hasInstrumentId={}, hasPaymentToken={})",
                subscription.getId(),
                payment.getId(),
                !isBlank(customerToken),
                !isBlank(paymentInstrumentId),
                !isBlank(paymentToken));

        if (!hasReusableInstrument && !isBlank(paymentToken)) {
            log.warn("CyberSource callback returned payment token without reusable customer/instrument IDs for subscription {}. " +
                    "Auto-renew will stay disabled until reusable IDs are available.", subscription.getId());
        }
    }

    private Optional<Subscription> resolveSubscriptionForRecurringTokenCapture(Payment payment) {
        if (payment.getSubscriptionId() != null) {
            return subscriptionRepository.findById(payment.getSubscriptionId());
        }

        if (payment.getUserId() != null) {
            return getActiveSubscription(payment.getUserId());
        }

        return Optional.empty();
    }

    private String extractLastFour(String maskedCard) {
        if (maskedCard == null || maskedCard.isBlank()) {
            return null;
        }

        String digitsOnly = maskedCard.replaceAll("\\D", "");
        if (digitsOnly.length() >= 4) {
            return digitsOnly.substring(digitsOnly.length() - 4);
        }

        if (maskedCard.length() >= 4) {
            return maskedCard.substring(maskedCard.length() - 4);
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /**
     * Quote amount/details for plan purchase or upgrade before creating a unified invoice.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> quotePlanInvoice(UUID userId, UUID planId, BillingInterval billingInterval) {
        if (userId == null || planId == null) {
            throw new IllegalArgumentException("userId and planId are required");
        }

        SubscriptionPlan targetPlan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));

        if (!Boolean.TRUE.equals(targetPlan.getIsActive())) {
            throw new IllegalStateException("Selected subscription plan is not active");
        }
        if (!targetPlan.supportsIndividualPurchases()) {
            throw new IllegalStateException("Selected plan is only available for corporate purchase");
        }

        BillingInterval targetInterval = validateBillingInterval(targetPlan, billingInterval);
        BigDecimal targetAmount = resolvePlanPrice(targetPlan, targetInterval);

        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("userId", userId);
        quote.put("planId", targetPlan.getId());
        quote.put("planName", targetPlan.getName());
        quote.put("currency", targetPlan.getCurrency());
        quote.put("billingInterval", targetInterval);
        quote.put("requiresPayment", Boolean.TRUE);

        Optional<Subscription> activeSubscriptionOpt = getActiveSubscription(userId);
        if (activeSubscriptionOpt.isPresent()) {
            Subscription currentSubscription = activeSubscriptionOpt.get();
            SubscriptionPlan currentPlan = currentSubscription.getPlan();
            BillingInterval currentInterval = resolveBillingInterval(currentSubscription);

            if (currentPlan != null && planId.equals(currentPlan.getId()) && currentInterval == targetInterval) {
                throw new IllegalStateException("User is already on the selected plan");
            }

            if (currentPlan != null
                    && currentPlan.getDisplayOrder() != null
                    && targetPlan.getDisplayOrder() != null
                    && targetPlan.getDisplayOrder() < currentPlan.getDisplayOrder()) {
                throw new IllegalStateException("Selected plan must be equal or higher than current plan");
            }

            BigDecimal amount = calculatePlanChangeAmount(
                    currentSubscription,
                    currentPlan != null ? currentPlan : targetPlan,
                    currentInterval,
                    targetPlan,
                    targetInterval
            );

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                quote.put("context", "PLAN_UPGRADE");
                quote.put("amount", BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
                quote.put("description", String.format("Switch subscription to %s (%s billing)",
                        targetPlan.getName(), formatBillingInterval(targetInterval)));
                quote.put("subscriptionId", currentSubscription.getId());
                quote.put("currentPlanId", currentPlan != null ? currentPlan.getId() : null);
                quote.put("currentBillingInterval", currentInterval);
                quote.put("requiresPayment", Boolean.FALSE);
                return quote;
            }

            quote.put("context", "PLAN_UPGRADE");
            quote.put("amount", amount.setScale(2, java.math.RoundingMode.HALF_UP));
            quote.put("description", String.format("Upgrade subscription to %s (%s billing)",
                    targetPlan.getName(), formatBillingInterval(targetInterval)));
            quote.put("subscriptionId", currentSubscription.getId());
            quote.put("currentPlanId", currentPlan != null ? currentPlan.getId() : null);
            quote.put("currentBillingInterval", currentInterval);
            return quote;
        }

        BigDecimal amount = targetAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            quote.put("context", "PLAN_PURCHASE");
            quote.put("amount", BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
            quote.put("description", String.format("Activate subscription plan: %s (%s billing)",
                    targetPlan.getName(), formatBillingInterval(targetInterval)));
            quote.put("subscriptionId", null);
            quote.put("currentPlanId", null);
            quote.put("requiresPayment", Boolean.FALSE);
            return quote;
        }

        quote.put("context", "PLAN_PURCHASE");
        quote.put("amount", amount);
        quote.put("description", String.format("Purchase subscription plan: %s (%s billing)",
                targetPlan.getName(), formatBillingInterval(targetInterval)));
        quote.put("subscriptionId", null);
        quote.put("currentPlanId", null);
        return quote;
    }

    /**
     * Quote amount/details for add-on purchase before creating a unified invoice.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> quoteAddonInvoice(UUID userId, int quantity) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        if (quantity <= 0 || quantity > 50) {
            throw new IllegalArgumentException("Invalid quantity. Must be between 1 and 50");
        }

        Subscription subscription = getActiveSubscription(userId)
                .orElseThrow(() -> new IllegalStateException("No active subscription found"));

        SubscriptionPlan plan = subscription.getPlan();
        if (plan == null || !Boolean.TRUE.equals(plan.getAllowsAddons())) {
            throw new IllegalStateException("This subscription plan does not allow add-ons");
        }

        if (plan.getAddonSessionCost() == null || plan.getAddonSessionCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Add-on pricing is not configured for this subscription plan");
        }

        BigDecimal costPerSession = plan.getAddonSessionCost().setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal amount = costPerSession
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("context", "SESSION_ADDON");
        quote.put("userId", userId);
        quote.put("subscriptionId", subscription.getId());
        quote.put("planId", plan.getId());
        quote.put("quantity", quantity);
        quote.put("costPerSession", costPerSession);
        quote.put("amount", amount);
        quote.put("currency", plan.getCurrency());
        quote.put("description", String.format("Purchase %d additional mentor session%s",
                quantity, quantity > 1 ? "s" : ""));
        return quote;
    }

    /**
     * Consume a session (tries subscription first, then add-ons)
     */
    public void consumeSessionSmart(UUID userId) {
        consumeSession(userId);
    }

    /**
     * Get user's add-ons
     */
    @Transactional(readOnly = true)
    public List<SubscriptionAddon> getUserAddons(UUID userId) {
        Optional<Subscription> subscriptionOpt = getActiveSubscription(userId);

        if (subscriptionOpt.isEmpty()) {
            return List.of();
        }

        return addonRepository.findBySubscriptionIdOrderByPurchasedAtDesc(subscriptionOpt.get().getId());
    }

    /**
     * Process expired add-ons (scheduled job)
     */
    public void processExpiredAddons() {
        log.info("Processing expired add-ons");

        List<SubscriptionAddon> expiredAddons = addonRepository.findExpiredAddons(LocalDateTime.now());

        for (SubscriptionAddon addon : expiredAddons) {
            addon.setStatus(SubscriptionAddon.AddonStatus.EXPIRED);
            addonRepository.save(addon);
            log.info("Marked addon {} as expired", addon.getId());
        }

        log.info("Processed {} expired add-ons", expiredAddons.size());
    }
}
