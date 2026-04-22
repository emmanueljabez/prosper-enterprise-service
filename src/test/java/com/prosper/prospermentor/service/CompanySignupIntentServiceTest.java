package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CreateCompanyRequest;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySignupIntent;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.repository.CompanySignupIntentRepository;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySignupIntentServiceTest {

    @Mock
    private CompanySignupIntentRepository companySignupIntentRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private CompanyService companyService;

    @InjectMocks
    private CompanySignupIntentService companySignupIntentService;

    @Test
    void createIntent_shouldPersistPricingDrivenIntentWithPendingCompanyRegistration() {
        UUID planId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(planId);
        plan.setIsActive(true);
        plan.setPlanAudience(SubscriptionPlan.PlanAudience.CORPORATE);

        Company company = new Company();
        company.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        company.setName("Acme Airways");
        company.setEmailAddress("ops@acme.test");
        company.setPhoneNumber("+254700000000");
        company.setRegistrationToken("reg-token");
        company.setRegistrationCompleted(false);

        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(companySignupIntentRepository.save(any(CompanySignupIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(companyService.createPendingCompanyRegistration(any(CreateCompanyRequest.class))).thenReturn(company);

        CompanySignupIntent intent = companySignupIntentService.createIntent(
                "Acme Airways",
                "ops@acme.test",
                "+254700000000",
                "Ada",
                "Lovelace",
                planId,
                40
        );

        assertThat(intent.getCompany().getId()).isEqualTo(company.getId());
        assertThat(intent.getCompanyRegistrationToken()).isEqualTo("reg-token");
        assertThat(intent.getTargetPlanId()).isEqualTo(planId);
        assertThat(intent.getTargetSessionCount()).isEqualTo(40);
        assertThat(intent.getToken()).isNotBlank();
        assertThat(intent.getStatus()).isEqualTo(CompanySignupIntent.SignupIntentStatus.PENDING);
    }
}
