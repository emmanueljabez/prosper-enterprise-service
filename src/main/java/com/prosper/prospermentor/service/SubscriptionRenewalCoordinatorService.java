package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates invoice-first subscription renewal flow for both scheduled auto-renewals
 * and user-triggered "renew now" operations.
 */
@Service
@Slf4j
public class SubscriptionRenewalCoordinatorService {

    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceService invoiceService;
    private final CyberSourceService cyberSourceService;

    public SubscriptionRenewalCoordinatorService(SubscriptionRepository subscriptionRepository,
                                                 InvoiceService invoiceService,
                                                 CyberSourceService cyberSourceService) {
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceService = invoiceService;
        this.cyberSourceService = cyberSourceService;
    }

    @Transactional
    public RenewalExecutionResult attemptAutomaticRenewal(UUID subscriptionId) {
        if (subscriptionId == null) {
            return RenewalExecutionResult.failed("subscriptionId is required", null, null);
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return RenewalExecutionResult.failed("Subscription not found", null, null);
        }

        return executeInvoiceFirstRenewal(subscription, "AUTO", true);
    }

    @Transactional
    public RenewalExecutionResult initiateManualRenewal(UUID userId) {
        if (userId == null) {
            return RenewalExecutionResult.failed("userId is required", null, null);
        }

        Optional<Subscription> subscriptionOpt = resolveLatestRenewableSubscription(userId);
        if (subscriptionOpt.isEmpty()) {
            return RenewalExecutionResult.failed("No renewable subscription found for user", null, null);
        }

        return executeInvoiceFirstRenewal(subscriptionOpt.get(), "MANUAL", true);
    }

    private RenewalExecutionResult executeInvoiceFirstRenewal(Subscription subscription,
                                                              String renewalMode,
                                                              boolean attemptAutoCharge) {
        Invoice renewalInvoice = invoiceService.createOrGetSubscriptionRenewalInvoice(subscription, renewalMode);
        String paymentUrl = invoiceService.buildPaymentUrl(renewalInvoice);

        if (!attemptAutoCharge || !subscription.hasAutoRenewPaymentMethod()) {
            return RenewalExecutionResult.requiresManualPayment(
                    "Renewal invoice created. Complete payment from the invoice page.",
                    renewalInvoice,
                    paymentUrl
            );
        }

        CyberSourceService.RecurringChargeResult recurringResult =
                cyberSourceService.chargeSubscriptionAutoRenewal(subscription, renewalInvoice, renewalMode);

        if (recurringResult.isSuccess()) {
            return RenewalExecutionResult.success(
                    "Subscription renewed successfully",
                    renewalInvoice,
                    paymentUrl,
                    recurringResult.getPaymentId(),
                    recurringResult.getTransactionId()
            );
        }

        log.warn("Invoice-first renewal auto-charge failed for subscription {}: {}",
                subscription.getId(), recurringResult.getMessage());
        return RenewalExecutionResult.failed(
                recurringResult.getMessage(),
                renewalInvoice,
                paymentUrl,
                recurringResult.getPaymentId()
        );
    }

    private Optional<Subscription> resolveLatestRenewableSubscription(UUID userId) {
        List<Subscription> subscriptions = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return subscriptions.stream()
                .filter(this::isRenewableStatus)
                .findFirst();
    }

    private boolean isRenewableStatus(Subscription subscription) {
        if (subscription == null || subscription.getStatus() == null) {
            return false;
        }
        String status = subscription.getStatus().name().toUpperCase(Locale.ROOT);
        return "ACTIVE".equals(status)
                || "TRIAL".equals(status)
                || "SUSPENDED".equals(status)
                || "EXPIRED".equals(status);
    }

    public static class RenewalExecutionResult {
        private final boolean success;
        private final boolean chargedAutomatically;
        private final String message;
        private final UUID invoiceId;
        private final String invoiceNumber;
        private final String paymentUrl;
        private final UUID paymentId;
        private final String transactionId;

        private RenewalExecutionResult(boolean success,
                                       boolean chargedAutomatically,
                                       String message,
                                       UUID invoiceId,
                                       String invoiceNumber,
                                       String paymentUrl,
                                       UUID paymentId,
                                       String transactionId) {
            this.success = success;
            this.chargedAutomatically = chargedAutomatically;
            this.message = message;
            this.invoiceId = invoiceId;
            this.invoiceNumber = invoiceNumber;
            this.paymentUrl = paymentUrl;
            this.paymentId = paymentId;
            this.transactionId = transactionId;
        }

        public static RenewalExecutionResult success(String message,
                                                     Invoice invoice,
                                                     String paymentUrl,
                                                     UUID paymentId,
                                                     String transactionId) {
            return new RenewalExecutionResult(
                    true,
                    true,
                    message,
                    invoice != null ? invoice.getId() : null,
                    invoice != null ? invoice.getInvoiceNumber() : null,
                    paymentUrl,
                    paymentId,
                    transactionId
            );
        }

        public static RenewalExecutionResult requiresManualPayment(String message,
                                                                   Invoice invoice,
                                                                   String paymentUrl) {
            return new RenewalExecutionResult(
                    false,
                    false,
                    message,
                    invoice != null ? invoice.getId() : null,
                    invoice != null ? invoice.getInvoiceNumber() : null,
                    paymentUrl,
                    null,
                    null
            );
        }

        public static RenewalExecutionResult failed(String message, Invoice invoice, String paymentUrl) {
            return new RenewalExecutionResult(
                    false,
                    false,
                    message,
                    invoice != null ? invoice.getId() : null,
                    invoice != null ? invoice.getInvoiceNumber() : null,
                    paymentUrl,
                    null,
                    null
            );
        }

        public static RenewalExecutionResult failed(String message, Invoice invoice, String paymentUrl, UUID paymentId) {
            return new RenewalExecutionResult(
                    false,
                    false,
                    message,
                    invoice != null ? invoice.getId() : null,
                    invoice != null ? invoice.getInvoiceNumber() : null,
                    paymentUrl,
                    paymentId,
                    null
            );
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isChargedAutomatically() {
            return chargedAutomatically;
        }

        public String getMessage() {
            return message;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }

        public String getInvoiceNumber() {
            return invoiceNumber;
        }

        public String getPaymentUrl() {
            return paymentUrl;
        }

        public UUID getPaymentId() {
            return paymentId;
        }

        public String getTransactionId() {
            return transactionId;
        }
    }
}
