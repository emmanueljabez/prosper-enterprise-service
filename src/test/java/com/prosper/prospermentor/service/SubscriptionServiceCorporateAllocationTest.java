package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.SessionBookingEligibility;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.EmployeeSessionAllocation;
import com.prosper.prospermentor.entity.Feature;
import com.prosper.prospermentor.entity.PlanFeature;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionAddon;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceCorporateAllocationTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private MpesaService mpesaService;
    @Mock
    private com.prosper.prospermentor.repository.SubscriptionAddonRepository addonRepository;
    @Mock
    private com.prosper.prospermentor.repository.FeatureRepository featureRepository;
    @Mock
    private CurrencyService currencyService;
    @Mock
    private CompanySubscriptionService companySubscriptionService;
    @Mock
    private EmployeeSessionAllocationService employeeSessionAllocationService;
    @Mock
    private PersonalSessionCreditService personalSessionCreditService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void checkSessionBookingEligibility_shouldUseEmployeeAllocationBalance() {
        UUID userId = UUID.randomUUID();
        Company company = new Company();
        company.setId(UUID.randomUUID());

        EmployeeSessionAllocation allocation = new EmployeeSessionAllocation();
        allocation.setId(UUID.randomUUID());
        allocation.setCompany(company);
        allocation.setAvailableBalance(2);

        when(employeeSessionAllocationService.findActiveAllocationForProfile(userId)).thenReturn(Optional.of(allocation));

        SessionBookingEligibility eligibility = subscriptionService.checkSessionBookingEligibility(userId);

        assertThat(eligibility.isCanBook()).isTrue();
        assertThat(eligibility.getSessionsRemaining()).isEqualTo(2);
        assertThat(eligibility.getSubscriptionSource()).isEqualTo(SessionBookingEligibility.SubscriptionSource.CORPORATE);
        assertThat(eligibility.getCompanyId()).isEqualTo(company.getId());
    }

    @Test
    void checkSessionBookingEligibility_shouldUsePersonalCreditWhenNoCorporateAllocationExists() {
        UUID userId = UUID.randomUUID();

        when(employeeSessionAllocationService.findActiveAllocationForProfile(userId)).thenReturn(Optional.empty());
        when(companySubscriptionService.findActiveMembershipForUser(userId)).thenReturn(Optional.empty());
        when(personalSessionCreditService.getAvailableCreditCount(userId)).thenReturn(1);

        SessionBookingEligibility eligibility = subscriptionService.checkSessionBookingEligibility(userId);

        assertThat(eligibility.isCanBook()).isTrue();
        assertThat(eligibility.getSessionsRemaining()).isEqualTo(1);
        assertThat(eligibility.getSubscriptionSource()).isEqualTo(SessionBookingEligibility.SubscriptionSource.PERSONAL_CREDIT);
    }

    @Test
    void consumeSessionForBooking_whenSubscriptionRemaining_shouldReturnSubscriptionConsumption() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeSubscription(userId, 3, 1);

        when(employeeSessionAllocationService.findActiveAllocationForProfile(userId)).thenReturn(Optional.empty());
        when(companySubscriptionService.findActiveMembershipForUser(userId)).thenReturn(Optional.empty());
        when(personalSessionCreditService.getAvailableCreditCount(userId)).thenReturn(0);
        when(subscriptionRepository.findActiveSubscriptionByUserId(any(UUID.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(subscription));
        when(addonRepository.getTotalRemainingUnits(eq(subscription.getId()), eq("EXTRA_SESSION"), any(LocalDateTime.class)))
                .thenReturn(0);

        SubscriptionService.SessionConsumptionResult result = subscriptionService.consumeSessionForBooking(userId);

        assertThat(result.source()).isEqualTo(SubscriptionService.SessionConsumptionSource.INDIVIDUAL_SUBSCRIPTION);
        assertThat(result.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(result.addonId()).isNull();
        assertThat(subscription.getSessionsUsed()).isEqualTo(2);
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void consumeSessionForBooking_whenPlanExhaustedAndAddonAvailable_shouldReturnAddonConsumption() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeSubscription(userId, 1, 1);
        SubscriptionAddon addon = addon(subscription, 1, 0, SubscriptionAddon.AddonStatus.ACTIVE);

        when(employeeSessionAllocationService.findActiveAllocationForProfile(userId)).thenReturn(Optional.empty());
        when(companySubscriptionService.findActiveMembershipForUser(userId)).thenReturn(Optional.empty());
        when(personalSessionCreditService.getAvailableCreditCount(userId)).thenReturn(0);
        when(subscriptionRepository.findActiveSubscriptionByUserId(any(UUID.class), any(LocalDateTime.class)))
                .thenReturn(Optional.of(subscription));
        when(addonRepository.getTotalRemainingUnits(eq(subscription.getId()), eq("EXTRA_SESSION"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(addonRepository.findActiveAddonsWithRemaining(any(UUID.class), any(LocalDateTime.class)))
                .thenReturn(List.of(addon));

        SubscriptionService.SessionConsumptionResult result = subscriptionService.consumeSessionForBooking(userId);

        assertThat(result.source()).isEqualTo(SubscriptionService.SessionConsumptionSource.SUBSCRIPTION_ADDON);
        assertThat(result.subscriptionId()).isEqualTo(subscription.getId());
        assertThat(result.addonId()).isEqualTo(addon.getId());
        assertThat(addon.getUsed()).isEqualTo(1);
        assertThat(addon.getStatus()).isEqualTo(SubscriptionAddon.AddonStatus.EXHAUSTED);
        verify(addonRepository).save(addon);
    }

    @Test
    void returnConsumedSessionForDeclinedBooking_whenAddonTracked_shouldRestoreAddonUnit() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeSubscription(userId, 1, 1);
        SubscriptionAddon addon = addon(subscription, 1, 1, SubscriptionAddon.AddonStatus.EXHAUSTED);

        when(addonRepository.findById(addon.getId())).thenReturn(Optional.of(addon));

        subscriptionService.returnConsumedSessionForDeclinedBooking(userId, subscription.getId(), addon.getId());

        assertThat(addon.getUsed()).isZero();
        assertThat(addon.getStatus()).isEqualTo(SubscriptionAddon.AddonStatus.ACTIVE);
        verify(addonRepository).save(addon);
    }

    @Test
    void returnConsumedSessionForDeclinedBooking_whenSubscriptionTracked_shouldDecrementSubscriptionUsage() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = activeSubscription(userId, 3, 2);

        when(subscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));

        subscriptionService.returnConsumedSessionForDeclinedBooking(userId, subscription.getId(), null);

        assertThat(subscription.getSessionsUsed()).isEqualTo(1);
        verify(subscriptionRepository).save(subscription);
    }

    private Subscription activeSubscription(UUID userId, int sessionsPerMonth, int sessionsUsed) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setName("All Access");
        plan.setCode("ALL_ACCESS");
        plan.setCost(BigDecimal.valueOf(20));
        plan.setCurrency("KES");
        plan.setSessionsPerPeriod(sessionsPerMonth);
        plan.setAllowsAddons(true);
        plan.setAddonSessionCost(BigDecimal.valueOf(20));
        plan.getPlanFeatures().add(mentorSessionFeature(plan));

        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setSessionsPerMonth(sessionsPerMonth);
        subscription.setSessionsUsed(sessionsUsed);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now().minusDays(1));
        subscription.setEndDate(LocalDateTime.now().plusDays(30));
        subscription.setCurrentPeriodStart(LocalDateTime.now().minusDays(1));
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusDays(30));
        return subscription;
    }

    private PlanFeature mentorSessionFeature(SubscriptionPlan plan) {
        Feature feature = new Feature();
        feature.setId(UUID.randomUUID());
        feature.setCode("MENTOR_SESSION");
        feature.setName("Mentor Session");
        feature.setType(Feature.FeatureType.MENTOR_SESSION);

        PlanFeature planFeature = new PlanFeature();
        planFeature.setId(UUID.randomUUID());
        planFeature.setPlan(plan);
        planFeature.setFeature(feature);
        planFeature.setEnabled(true);
        planFeature.setLimitValue(1);
        return planFeature;
    }

    private SubscriptionAddon addon(Subscription subscription, int quantity, int used, SubscriptionAddon.AddonStatus status) {
        SubscriptionAddon addon = new SubscriptionAddon();
        addon.setId(UUID.randomUUID());
        addon.setSubscription(subscription);
        addon.setAddonType("EXTRA_SESSION");
        addon.setAddonName("Extra Session");
        addon.setQuantity(quantity);
        addon.setUsed(used);
        addon.setTotalCost(BigDecimal.valueOf(20));
        addon.setPurchasedAt(LocalDateTime.now().minusMinutes(10));
        addon.setStatus(status);
        return addon;
    }
}
