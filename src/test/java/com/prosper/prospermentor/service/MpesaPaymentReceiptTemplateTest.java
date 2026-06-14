package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Profile;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MpesaPaymentReceiptTemplateTest {

    @Test
    void paymentReceiptTemplate_shouldRenderRedesignedReceiptWithExistingDataAndLink() {
        Payment payment = new Payment();
        payment.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        payment.setUserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        payment.setPaymentType(Payment.PaymentType.SESSION_BOOKING);
        payment.setPaymentMethod(Payment.PaymentMethod.CARD);
        payment.setAmount(new BigDecimal("4000.00"));
        payment.setCurrency("KES");
        payment.setCardType("VISA");
        payment.setCardLastFour("4412");
        payment.setCompletedAt(LocalDateTime.of(2026, 6, 2, 1, 57));
        payment.setCreatedAt(LocalDateTime.of(2026, 6, 2, 1, 55));

        Profile profile = new Profile();
        profile.setEmail("customer@example.com");
        profile.setFirstName("Emmanuel");
        profile.setLastName("Otieno");

        Context context = new Context();
        context.setVariable("appName", "ProsperMentor");
        context.setVariable("baseUrl", "https://enterprise.prospermentor.com/app/receipts/11111111-1111-1111-1111-111111111111");
        context.setVariable("payment", payment);
        context.setVariable("profile", profile);
        context.setVariable("customerName", "Emmanuel Otieno");
        context.setVariable("paymentDate", "Jun 02, 2026 01:57:00");
        context.setVariable("paymentType", "Session Booking");
        context.setVariable("paymentMethodDisplay", "Card (**** 4412)");
        context.setVariable("transactionReference", "EM-2026-0001");

        String html = templateEngine().process("email/payment-receipt", context);

        assertThat(html)
                .contains("THANK YOU")
                .contains("Payment<br>")
                .contains("Successful.")
                .contains("Total Amount Paid")
                .contains("KES 4000.00")
                .contains("RECEIPT NUMBER")
                .contains("EM-2026-0001")
                .contains("SERVICE PURCHASED")
                .contains("Session Booking")
                .contains("Jun 02, 2026 01:57:00")
                .contains("VISA")
                .contains("**** 4412")
                .contains("Paid via Card")
                .contains("View Full Receipt")
                .contains("https://enterprise.prospermentor.com/app/receipts/11111111-1111-1111-1111-111111111111")
                .contains("Contact Support")
                .contains("customer@example.com");
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
