package com.prosper.prospermentor.service;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceFreeTrialTest {

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
    void activateFreeTrial_shouldCreateTrialSubscriptionWhenUserHasNoTrialHistory() {
        UUID userId = UUID.randomUUID();
        SubscriptionPlan plan = allAccessPlan();

        when(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(subscriptionPlanRepository.findByCode("FREE_TRIAL")).thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("ALL_ACCESS")).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse<Subscription> response = subscriptionService.activateFreeTrial(userId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getStatus()).isEqualTo(Subscription.SubscriptionStatus.TRIAL);
        assertThat(response.getData().getIsTrial()).isTrue();
        assertThat(response.getData().getSessionsPerMonth()).isEqualTo(1);
        assertThat(response.getData().getSessionsUsed()).isZero();
        assertThat(response.getData().getAutoRenew()).isFalse();

        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(subscriptionCaptor.getValue().getPlan()).isEqualTo(plan);
        assertThat(subscriptionCaptor.getValue().getEndDate()).isAfter(LocalDateTime.now().plusDays(29));
    }

    @Test
    void activateFreeTrial_shouldRejectUserWhoAlreadyUsedTrial() {
        UUID userId = UUID.randomUUID();
        Subscription previousTrial = new Subscription();
        previousTrial.setId(UUID.randomUUID());
        previousTrial.setUserId(userId);
        previousTrial.setStatus(Subscription.SubscriptionStatus.EXPIRED);
        previousTrial.setIsTrial(true);

        when(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(previousTrial));

        ApiResponse<Subscription> response = subscriptionService.activateFreeTrial(userId);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("already used");
    }

    private SubscriptionPlan allAccessPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setName("All Access");
        plan.setCode("ALL_ACCESS");
        plan.setCost(BigDecimal.valueOf(4000));
        plan.setCurrency("KES");
        plan.setSessionsPerPeriod(1);
        plan.setDurationMonths(1);
        plan.setIsActive(true);
        return plan;
    }
}
