package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Company;
import com.prosper.prospermentor.entity.CompanySessionWallet;
import com.prosper.prospermentor.entity.CompanySubscription;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import com.prosper.prospermentor.repository.CompanyRepository;
import com.prosper.prospermentor.repository.CompanySessionWalletRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionMemberRepository;
import com.prosper.prospermentor.repository.CompanySubscriptionRepository;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySubscriptionServiceSessionWalletTest {

    @Mock
    private CompanySubscriptionRepository companySubscriptionRepository;
    @Mock
    private CompanySubscriptionMemberRepository companySubscriptionMemberRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private CompanySessionWalletRepository companySessionWalletRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private CompanySessionWalletService companySessionWalletService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CompanySubscriptionService companySubscriptionService;

    @Test
    void createCompanySubscription_shouldPriceInvoiceUsingSessionCount() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCost(new BigDecimal("4500"));
        plan.setCurrency("KES");
        plan.setIsActive(true);
        plan.setPlanAudience(SubscriptionPlan.PlanAudience.CORPORATE);
        plan.setName("Corporate");

        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionPlanRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(companySubscriptionRepository.findByCompany_IdOrderByCreatedAtDesc(company.getId())).thenReturn(java.util.List.of());
        when(companySubscriptionRepository.save(any(CompanySubscription.class))).thenAnswer(invocation -> {
            CompanySubscription subscription = invocation.getArgument(0);
            if (subscription.getId() == null) {
                subscription.setId(UUID.randomUUID());
            }
            return subscription;
        });
        when(invoiceService.createInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(invoice());
        when(invoiceService.buildPaymentUrl(any(Invoice.class))).thenReturn("https://pay.example.com/invoice");

        Map<String, Object> payload = companySubscriptionService.createCompanySubscription(
                company.getId(),
                plan.getId(),
                8,
                BillingInterval.MONTHLY,
                UUID.randomUUID(),
                "https://example.com/success",
                "https://example.com/cancel"
        );

        assertThat(payload).containsEntry("amount", new BigDecimal("36000.00"));
        assertThat(payload).containsEntry("sessionCount", 8);
        assertThat(payload).containsEntry("pricePerSession", new BigDecimal("4500"));
    }

    @Test
    void createCompanySubscription_shouldKeepExistingWalletSubscriptionActiveForTopUp() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCost(new BigDecimal("4500"));
        plan.setCurrency("KES");
        plan.setIsActive(true);
        plan.setPlanAudience(SubscriptionPlan.PlanAudience.CORPORATE);
        plan.setName("Corporate");

        CompanySubscription existingSubscription = new CompanySubscription();
        existingSubscription.setId(UUID.randomUUID());
        existingSubscription.setCompany(company);
        existingSubscription.setPlan(plan);
        existingSubscription.setStatus(CompanySubscription.CompanySubscriptionStatus.ACTIVE);
        existingSubscription.setStartDate(LocalDateTime.now().minusMonths(2));
        existingSubscription.setEndDate(LocalDateTime.now().minusDays(1));
        existingSubscription.setCurrentPeriodStart(LocalDateTime.now().minusMonths(1));
        existingSubscription.setCurrentPeriodEnd(LocalDateTime.now().minusDays(1));

        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(subscriptionPlanRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(companySubscriptionRepository.findByCompany_IdOrderByCreatedAtDesc(company.getId()))
                .thenReturn(java.util.List.of(existingSubscription));
        when(companySubscriptionRepository.save(any(CompanySubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceService.createInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(invoice());
        when(invoiceService.buildPaymentUrl(any(Invoice.class))).thenReturn("https://pay.example.com/invoice");

        companySubscriptionService.createCompanySubscription(
                company.getId(),
                plan.getId(),
                3,
                BillingInterval.MONTHLY,
                UUID.randomUUID(),
                "https://example.com/success",
                "https://example.com/cancel"
        );

        assertThat(existingSubscription.getStatus()).isEqualTo(CompanySubscription.CompanySubscriptionStatus.ACTIVE);
        assertThat(existingSubscription.getEndDate()).isNull();
        assertThat(existingSubscription.getCurrentPeriodEnd()).isNull();
    }

    @Test
    void applyInvoicePayment_shouldTopUpWalletOnCorporatePurchase() {
        CompanySubscription subscription = activeSubscription();

        when(companySubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findById(subscription.getPlan().getId())).thenReturn(Optional.of(subscription.getPlan()));
        when(companySubscriptionRepository.save(any(CompanySubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        companySubscriptionService.applyInvoicePayment(
                subscription.getId(),
                "COMPANY_SUBSCRIPTION_PURCHASE",
                BillingInterval.MONTHLY,
                subscription.getPlan().getId(),
                null,
                10
        );

        verify(companySessionWalletService).recordPurchase(
                eq(subscription.getId()),
                eq(subscription.getCompany().getId()),
                any(BigDecimal.class),
                eq(10),
                isNull(),
                eq("COMPANY_SUBSCRIPTION_PURCHASE")
        );
    }

    @Test
    void getCompanySubscriptionDetails_shouldIncludeWalletPayload() {
        CompanySubscription subscription = activeSubscription();

        CompanySessionWallet wallet = new CompanySessionWallet();
        wallet.setId(UUID.randomUUID());
        wallet.setCompany(subscription.getCompany());
        wallet.setCompanySubscription(subscription);
        wallet.setPricePerSessionSnapshot(new BigDecimal("4500"));
        wallet.setSessionsPurchasedTotal(12);
        wallet.setSessionsAllocatedTotal(4);
        wallet.setSessionsReturnedTotal(1);
        wallet.setSessionsAvailable(9);

        when(companySubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(companySessionWalletRepository.findByCompanySubscription_Id(subscription.getId())).thenReturn(Optional.of(wallet));

        Map<String, Object> payload = companySubscriptionService.getCompanySubscriptionDetails(subscription.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> walletPayload = (Map<String, Object>) payload.get("wallet");

        assertThat(payload).containsKey("wallet");
        assertThat(walletPayload).containsEntry("sessionsPurchased", 12);
    }

    @Test
    void getCompanySubscriptionDetails_shouldExposePendingSessionCountAndInvoiceToken() {
        CompanySubscription subscription = activeSubscription();
        subscription.setStatus(CompanySubscription.CompanySubscriptionStatus.PENDING_PAYMENT);
        subscription.setSeatsPurchased(10);

        Invoice invoice = invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setPublicToken("corp-pending-token");
        subscription.setLatestInvoiceId(invoice.getId());

        when(companySubscriptionRepository.findById(subscription.getId())).thenReturn(Optional.of(subscription));
        when(companySessionWalletRepository.findByCompanySubscription_Id(subscription.getId())).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceService.buildPaymentUrl(invoice)).thenReturn("https://enterprise.prospermentor.com/payment/invoice/corp-pending-token");

        Map<String, Object> payload = companySubscriptionService.getCompanySubscriptionDetails(subscription.getId());
        @SuppressWarnings("unchecked")
        Map<String, Object> latestInvoicePayload = (Map<String, Object>) payload.get("latestInvoice");

        assertThat(payload).containsEntry("seatsPurchased", 10);
        assertThat(latestInvoicePayload)
                .containsEntry("publicToken", "corp-pending-token")
                .containsEntry("sessionCount", 10);
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setAmount(new BigDecimal("36000.00"));
        invoice.setCurrency("KES");
        invoice.setInvoiceNumber("INV-001");
        invoice.setPublicToken("public-token");
        return invoice;
    }

    private CompanySubscription activeSubscription() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(UUID.randomUUID());
        plan.setCost(new BigDecimal("4500"));
        plan.setCurrency("KES");
        plan.setIsActive(true);
        plan.setPlanAudience(SubscriptionPlan.PlanAudience.CORPORATE);
        plan.setName("Corporate");

        CompanySubscription subscription = new CompanySubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setStatus(CompanySubscription.CompanySubscriptionStatus.ACTIVE);
        return subscription;
    }
}
