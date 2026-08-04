package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceRedirectNormalizationTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MpesaService mpesaService;
    @Mock
    private CyberSourceService cyberSourceService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invoiceService, "frontendUrl", "https://enterprise.prospermentor.com");
    }

    @Test
    void buildPaymentUrl_shouldUseConfiguredFrontendWhenInvoiceRedirectOriginIsLocalhost() {
        Invoice invoice = invoice();
        invoice.setRedirectSuccessUrl("http://localhost:3000/app/admin/activate?invoice_paid=1");
        invoice.setRedirectCancelUrl("http://localhost:3000/app/admin/activate?invoice_cancelled=1");

        String paymentUrl = invoiceService.buildPaymentUrl(invoice);

        assertThat(paymentUrl).isEqualTo("https://enterprise.prospermentor.com/payment/invoice/" + invoice.getPublicToken());
    }

    @Test
    void buildPublicInvoicePayload_shouldPreserveOriginatorRedirectUrls() {
        Invoice invoice = invoice();
        invoice.setRedirectSuccessUrl("http://localhost:3000/app/admin/activate?invoice_paid=1");
        invoice.setRedirectCancelUrl("http://localhost:3000/app/admin/activate?invoice_cancelled=1");

        when(invoiceRepository.findByPublicToken(invoice.getPublicToken())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findTopByInvoiceIdOrderByCreatedAtDesc(invoice.getId())).thenReturn(Optional.empty());

        Map<String, Object> payload = invoiceService.buildPublicInvoicePayload(invoice.getPublicToken());

        assertThat(payload)
                .containsEntry("redirectSuccessUrl", "http://localhost:3000/app/admin/activate?invoice_paid=1")
                .containsEntry("redirectCancelUrl", "http://localhost:3000/app/admin/activate?invoice_cancelled=1")
                .containsEntry("paymentUrl", "https://enterprise.prospermentor.com/payment/invoice/" + invoice.getPublicToken());
    }

    @Test
    void buildPublicInvoicePayload_shouldExposeNumericMpesaAccountReference() {
        Invoice invoice = invoice();

        when(invoiceRepository.findByPublicToken(invoice.getPublicToken())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findTopByInvoiceIdOrderByCreatedAtDesc(invoice.getId())).thenReturn(Optional.empty());

        Map<String, Object> payload = invoiceService.buildPublicInvoicePayload(invoice.getPublicToken());

        assertThat(payload.get("mpesaAccountReference"))
                .asString()
                .matches("\\d{8}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPublicInvoicePayload_shouldExposeLatestPaymentMpesaAccountReference() {
        Invoice invoice = invoice();
        Payment latestPayment = new Payment();
        latestPayment.setId(UUID.randomUUID());
        latestPayment.setStatus(Payment.PaymentStatus.PENDING);
        latestPayment.setPaymentMethod(Payment.PaymentMethod.MPESA);
        latestPayment.setGatewayReference("12345678");

        when(invoiceRepository.findByPublicToken(invoice.getPublicToken())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findTopByInvoiceIdOrderByCreatedAtDesc(invoice.getId())).thenReturn(Optional.of(latestPayment));

        Map<String, Object> payload = invoiceService.buildPublicInvoicePayload(invoice.getPublicToken());
        Map<String, Object> latestPaymentPayload = (Map<String, Object>) payload.get("latestPayment");

        assertThat(latestPaymentPayload).containsEntry("mpesaAccountReference", "12345678");
    }

    @Test
    void initiatePayment_shouldReturnMpesaAccountReferenceForStkPush() {
        Invoice invoice = invoice();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setPaymentMethod(Payment.PaymentMethod.MPESA);
        payment.setCheckoutRequestId("ws_CO_010620261234567890");
        payment.setGatewayReference("87654321");

        when(invoiceRepository.findByPublicToken(invoice.getPublicToken())).thenReturn(Optional.of(invoice));
        when(mpesaService.initiateSTKPush(
                eq(invoice.getPayerUserId()),
                eq(null),
                eq(null),
                eq(Payment.PaymentType.INVOICE),
                eq(invoice.getAmount()),
                eq("0712345678"),
                eq(invoice.getDescription())
        )).thenReturn(payment);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> payload = invoiceService.initiatePayment(
                invoice.getPublicToken(),
                "MPESA",
                "0712345678",
                null,
                null
        );

        assertThat(payload).containsEntry("mpesaAccountReference", "87654321");
    }

    private Invoice invoice() {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setPublicToken("corp-invoice-token");
        invoice.setInvoiceNumber("INV-20260424-TEST");
        invoice.setPayerUserId(UUID.randomUUID());
        invoice.setAmount(new BigDecimal("10.00"));
        invoice.setCurrency("KES");
        invoice.setStatus(Invoice.InvoiceStatus.OPEN);
        invoice.setDescription("Corporate invoice");
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setExpiresAt(LocalDateTime.now().plusDays(7));
        return invoice;
    }
}
