package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Session;
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
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MpesaServiceSessionPaymentTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private SessionRepository sessionRepository;
    @Mock private EmailService emailService;
    @Mock private ProfileRepository profileRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SpringTemplateEngine templateEngine;
    @Mock private CompanySubscriptionService companySubscriptionService;

    private MpesaService mpesaService;

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
    }

    @Test
    void sessionBookingPaymentSuccess_shouldMarkSessionPaidButKeepItPendingForMentorReview() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Session session = new Session();
        session.setId(sessionId);
        session.setStatus(Session.SessionStatus.PENDING);
        session.setPaymentStatus(Session.PaymentStatus.PENDING);
        session.setPaid(false);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setUserId(userId);
        payment.setPayerId(userId);
        payment.setRecipientId(UUID.randomUUID());
        payment.setSessionId(sessionId);
        payment.setPaymentType(Payment.PaymentType.SESSION_BOOKING);
        payment.setAmount(new BigDecimal("4000.00"));
        payment.setStatus(Payment.PaymentStatus.COMPLETED);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        mpesaService.applyPostPaymentActions(payment, true);

        assertThat(session.getPaymentStatus()).isEqualTo(Session.PaymentStatus.PAID);
        assertThat(session.getPaid()).isTrue();
        assertThat(session.getStatus()).isEqualTo(Session.SessionStatus.PENDING);
        assertThat(session.getConfirmedAt()).isNull();
    }
}
