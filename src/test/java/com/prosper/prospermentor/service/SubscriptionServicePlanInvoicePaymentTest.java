package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServicePlanInvoicePaymentTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private MpesaService mpesaService;
    @Mock private com.prosper.prospermentor.repository.SubscriptionAddonRepository addonRepository;
    @Mock private com.prosper.prospermentor.repository.FeatureRepository featureRepository;
    @Mock private CurrencyService currencyService;
    @Mock private CompanySubscriptionService companySubscriptionService;
    @Mock private EmployeeSessionAllocationService employeeSessionAllocationService;
    @Mock private PersonalSessionCreditService personalSessionCreditService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void applyPlanInvoicePayment_shouldReplenishSameOneTimePackagePurchase() {
        UUID userId = UUID.randomUUID();
        SubscriptionPlan pack3 = oneTimePlan("PACK_3", 3, 11);
        Subscription activePack = activeSubscription(userId, pack3, 3);

        when(subscriptionPlanRepository.findById(pack3.getId())).thenReturn(Optional.of(pack3));
        when(subscriptionRepository.findActiveSubscriptionByUserId(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activePack));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<Subscription> response =
                subscriptionService.applyPlanInvoicePayment(userId, pack3.getId(), BillingInterval.MONTHLY);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(activePack);

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        Subscription saved = subscriptionCaptor.getValue();
        assertThat(saved.getPlan()).isEqualTo(pack3);
        assertThat(saved.getSessionsPerMonth()).isEqualTo(3);
        assertThat(saved.getSessionsUsed()).isZero();
        assertThat(saved.getStatus()).isEqualTo(Subscription.SubscriptionStatus.ACTIVE);
        assertThat(saved.getIsTrial()).isFalse();
        assertThat(saved.getAutoRenew()).isFalse();
        assertThat(saved.getCurrentPeriodEnd()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void quotePlanInvoice_shouldChargeFullOneTimePackagePriceWhenUserHasActivePlan() {
        UUID userId = UUID.randomUUID();
        SubscriptionPlan pack3 = oneTimePlan("PACK_3", 3, 11);

        when(subscriptionPlanRepository.findById(pack3.getId())).thenReturn(Optional.of(pack3));

        var quote = subscriptionService.quotePlanInvoice(userId, pack3.getId(), BillingInterval.MONTHLY);

        assertThat(quote).containsEntry("context", "PLAN_PURCHASE");
        assertThat((BigDecimal) quote.get("amount")).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(quote).containsEntry("subscriptionId", null);
        assertThat(quote).containsEntry("currentPlanId", null);
        verify(subscriptionRepository, never()).findActiveSubscriptionByUserId(eq(userId), any(LocalDateTime.class));
    }

    private SubscriptionPlan oneTimePlan(String code, int sessions, int displayOrder) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setName(code.replace("_", " "));
        plan.setCode(code);
        plan.setCost(BigDecimal.valueOf(5));
        plan.setCurrency("KES");
        plan.setSessionsPerPeriod(sessions);
        plan.setDurationMonths(1);
        plan.setDisplayOrder(displayOrder);
        plan.setIsActive(true);
        plan.setBillingType(SubscriptionPlan.BillingType.ONE_TIME);
        return plan;
    }

    private Subscription activeSubscription(UUID userId, SubscriptionPlan plan, int sessionsUsed) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setBillingInterval(BillingInterval.MONTHLY);
        subscription.setSessionsPerMonth(plan.getSessionsPerPeriod());
        subscription.setSessionsUsed(sessionsUsed);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setIsTrial(false);
        subscription.setAutoRenew(false);
        subscription.setStartDate(now.minusDays(10));
        subscription.setEndDate(now.plusDays(20));
        subscription.setCurrentPeriodStart(now.minusDays(10));
        subscription.setCurrentPeriodEnd(now.plusDays(20));
        return subscription;
    }
}
