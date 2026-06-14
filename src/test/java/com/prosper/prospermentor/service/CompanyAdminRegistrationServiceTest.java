package com.prosper.prospermentor.service;

import com.prosper.prospermentor.dto.CompleteCompanySignupIntentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySignupIntent;
import com.prosper.prospermentor.model.ApiResponse;
import com.prosper.prospermentor.service.notification.CompanyNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminRegistrationServiceTest {

    @Mock
    private CompanySignupIntentService companySignupIntentService;
    @Mock
    private CompanyService companyService;
    @Mock
    private CompanySubscriptionService companySubscriptionService;
    @Mock
    private SupabaseAuthService supabaseAuthService;
    @Mock
    private CompanyNotificationService companyNotificationService;

    @InjectMocks
    private CompanyAdminRegistrationService companyAdminRegistrationService;

    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void completeIntent_shouldRequireEmailVerificationAndNotReturnAuthTokens() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID profileId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID companyId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        CompleteCompanySignupIntentRequest request = new CompleteCompanySignupIntentRequest();
        request.setEmail("admin@example.com");
        request.setPassword("Password123!");
        request.setFirstName("Admin");
        request.setLastName("User");
        request.setPhoneNumber("0720482575");

        Company company = new Company();
        company.setId(companyId);

        CompanySignupIntent intent = new CompanySignupIntent();
        intent.setCompany(company);
        intent.setCompanyRegistrationToken("registration-token");

        when(companySignupIntentService.requireActiveIntent("intent-token")).thenReturn(intent);
        ReflectionTestUtils.setField(companyAdminRegistrationService, "frontendUrl", "https://enterprise.prospermentor.com");

        when(supabaseAuthService.generateSignupConfirmationLink(
                "admin@example.com",
                "Password123!",
                "company",
                "Admin",
                "User",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1"
        )).thenReturn(Mono.just(realObjectMapper.readTree("""
                {
                  "action_link": "https://supabase.example.com/auth/v1/verify?token=abc&type=signup&redirect_to=https%3A%2F%2Fenterprise.prospermentor.com%2Fauth%2Flogin%3Femail_verified%3D1",
                  "hashed_token": "hashed-abc",
                  "user": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "email": "admin@example.com"
                  },
                  "session": null
                }
                """)));

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", profileId.toString());
        profile.put("email", "admin@example.com");
        profile.put("role", "company");

        Map<String, Object> companyPayload = new HashMap<>();
        companyPayload.put("linked", true);
        companyPayload.put("companyId", companyId);
        companyPayload.put("companyName", "Example Co");

        when(companyService.completeCompanyRegistrationWithProfile(
                "registration-token",
                userId,
                "admin@example.com",
                "Admin",
                "User",
                "0720482575",
                null,
                false
        )).thenReturn(ApiResponse.success("Company registration completed successfully", Map.of(
                "profile", profile,
                "company", companyPayload
        )));

        ResponseEntity<Object> response = companyAdminRegistrationService.completeIntent("intent-token", request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
                .containsEntry("emailVerificationRequired", true)
                .containsEntry("message", "Company account created. Verify your email, then sign in to continue.");
        assertThat(body).doesNotContainKeys("access_token", "refresh_token");

        verify(supabaseAuthService, never()).signInWithPassword(any(), any());
        verify(companyNotificationService).sendCompanyEmailConfirmation(
                "admin@example.com",
                "Admin",
                "Example Co",
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=hashed-abc&type=signup"
        );
        verify(companySignupIntentService).markCompleted("intent-token", userId, profileId);
    }

    @Test
    void resumePurchase_shouldCreateInvoiceUsingLinkedCompanyAndStoredSelection() {
        UUID companyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID planId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        Company company = new Company();
        company.setId(companyId);

        CompanySignupIntent intent = new CompanySignupIntent();
        intent.setCompany(company);
        intent.setTargetPlanId(planId);
        intent.setTargetSessionCount(25);

        when(companySignupIntentService.requirePurchasableIntent("intent-token")).thenReturn(intent);
        when(companySubscriptionService.createCompanySubscription(
                companyId,
                planId,
                25,
                BillingInterval.MONTHLY,
                userId,
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_paid=1",
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_cancelled=1"
        )).thenReturn(Map.of("paymentUrl", "https://pay.example.com/invoice"));

        Map<String, Object> payload = companyAdminRegistrationService.resumePurchase(
                "intent-token",
                userId,
                null,
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_paid=1",
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_cancelled=1"
        );

        assertThat(payload).containsEntry("paymentUrl", "https://pay.example.com/invoice");
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeIntent_shouldFallbackToActionLinkTokenWhenHashedTokenIsMissing() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID profileId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID companyId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        CompleteCompanySignupIntentRequest request = new CompleteCompanySignupIntentRequest();
        request.setEmail("admin@example.com");
        request.setPassword("Password123!");
        request.setFirstName("Admin");
        request.setLastName("User");
        request.setPhoneNumber("0720482575");

        Company company = new Company();
        company.setId(companyId);

        CompanySignupIntent intent = new CompanySignupIntent();
        intent.setCompany(company);
        intent.setCompanyRegistrationToken("registration-token");

        when(companySignupIntentService.requireActiveIntent("intent-token")).thenReturn(intent);
        ReflectionTestUtils.setField(companyAdminRegistrationService, "frontendUrl", "https://enterprise.prospermentor.com/");

        when(supabaseAuthService.generateSignupConfirmationLink(
                "admin@example.com",
                "Password123!",
                "company",
                "Admin",
                "User",
                "https://enterprise.prospermentor.com/auth/login?email_verified=1"
        )).thenReturn(Mono.just(realObjectMapper.readTree("""
                {
                  "action_link": "https://supabase.example.com/auth/v1/verify?token=abc&type=signup&redirect_to=https://prospermentor.com",
                  "user": {
                    "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "email": "admin@example.com"
                  },
                  "session": null
                }
                """)));

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", profileId.toString());
        profile.put("email", "admin@example.com");
        profile.put("role", "company");

        Map<String, Object> companyPayload = new HashMap<>();
        companyPayload.put("linked", true);
        companyPayload.put("companyId", companyId);
        companyPayload.put("companyName", "Example Co");

        when(companyService.completeCompanyRegistrationWithProfile(
                "registration-token",
                userId,
                "admin@example.com",
                "Admin",
                "User",
                "0720482575",
                null,
                false
        )).thenReturn(ApiResponse.success("Company registration completed successfully", Map.of(
                "profile", profile,
                "company", companyPayload
        )));

        ResponseEntity<Object> response = companyAdminRegistrationService.completeIntent("intent-token", request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(companyNotificationService).sendCompanyEmailConfirmation(
                "admin@example.com",
                "Admin",
                "Example Co",
                "https://enterprise.prospermentor.com/auth/confirm-email?token_hash=abc&type=signup"
        );
    }

    @Test
    void resumePurchase_shouldAllowActivationToOverrideStoredSessionCount() {
        UUID companyId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID planId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        Company company = new Company();
        company.setId(companyId);

        CompanySignupIntent intent = new CompanySignupIntent();
        intent.setCompany(company);
        intent.setTargetPlanId(planId);
        intent.setTargetSessionCount(25);

        when(companySignupIntentService.requirePurchasableIntent("intent-token")).thenReturn(intent);
        when(companySignupIntentService.updateTargetSessionCount("intent-token", 40)).thenAnswer(invocation -> {
            intent.setTargetSessionCount(invocation.getArgument(1));
            return intent;
        });
        when(companySubscriptionService.createCompanySubscription(
                companyId,
                planId,
                40,
                BillingInterval.MONTHLY,
                userId,
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_paid=1",
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_cancelled=1"
        )).thenReturn(Map.of("paymentUrl", "https://pay.example.com/invoice"));

        Map<String, Object> payload = companyAdminRegistrationService.resumePurchase(
                "intent-token",
                userId,
                40,
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_paid=1",
                "https://enterprise.prospermentor.com/app/admin/activate?invoice_cancelled=1"
        );

        assertThat(payload).containsEntry("paymentUrl", "https://pay.example.com/invoice");
        assertThat(intent.getTargetSessionCount()).isEqualTo(40);
    }
}
