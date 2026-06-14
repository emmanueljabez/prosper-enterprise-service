package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@ExtendWith(MockitoExtension.class)
class MpesaServiceAccountReferenceTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private SpringTemplateEngine templateEngine;
    @Mock
    private CompanySubscriptionService companySubscriptionService;

    private MpesaService mpesaService;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        mpesaService = new MpesaService(
                paymentRepository,
                subscriptionService,
                sessionRepository,
                emailService,
                profileRepository,
                subscriptionRepository,
                invoiceRepository,
                templateEngine,
                companySubscriptionService
        );

        ReflectionTestUtils.setField(mpesaService, "consumerKey", "consumer-key");
        ReflectionTestUtils.setField(mpesaService, "consumerSecret", "consumer-secret");
        ReflectionTestUtils.setField(mpesaService, "mpesaApiUrl", "https://sandbox.safaricom.co.ke");
        ReflectionTestUtils.setField(mpesaService, "shortcode", "4045031");
        ReflectionTestUtils.setField(mpesaService, "passkey", "passkey");
        ReflectionTestUtils.setField(mpesaService, "callbackUrl", "https://api.prospermentor.com/api/v1/payments/confirmc2b");

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(mpesaService, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(PAYMENT_ID);
            }
            return payment;
        });
    }

    @Test
    void initiateStkPush_shouldUseNumericGatewayReferenceAsMpesaAccountReference() {
        server.expect(requestTo("https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"access_token\":\"access-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.AccountReference").value(matchesPattern("\\d{8}")))
                .andRespond(withSuccess("""
                        {
                          "ResponseCode": "0",
                          "CheckoutRequestID": "ws_CO_010620261234567890",
                          "MerchantRequestID": "29115-34620561-1"
                        }
                        """, MediaType.APPLICATION_JSON));

        Payment payment = mpesaService.initiateSTKPush(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                null,
                null,
                Payment.PaymentType.INVOICE,
                new BigDecimal("4000.00"),
                "0712345678",
                "Invoice payment"
        );

        assertThat(payment.getGatewayReference())
                .isNotBlank()
                .matches("\\d{8}");
        assertThat(payment.getGatewayReference()).doesNotContain("-");
        server.verify();
    }
}
