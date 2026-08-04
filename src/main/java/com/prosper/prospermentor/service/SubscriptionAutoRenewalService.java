package com.prosper.prospermentor.service;

import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled service that attempts recurring CyberSource charges for subscriptions
 * that have reached their billing boundary.
 */
@Service
@Slf4j
public class SubscriptionAutoRenewalService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRenewalCoordinatorService subscriptionRenewalCoordinatorService;

    @Value("${subscriptions.auto-renew.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${cybersource.recurring.enabled:false}")
    private boolean recurringGatewayEnabled;

    public SubscriptionAutoRenewalService(SubscriptionRepository subscriptionRepository,
                                          SubscriptionRenewalCoordinatorService subscriptionRenewalCoordinatorService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionRenewalCoordinatorService = subscriptionRenewalCoordinatorService;
    }

    @Scheduled(cron = "${subscriptions.auto-renew.cron:0 */10 * * * *}")
    public void processDueAutoRenewals() {
        if (!schedulerEnabled) {
            return;
        }

        if (!recurringGatewayEnabled) {
            log.debug("Skipping subscription auto-renew scheduler because CyberSource recurring is disabled");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<UUID> dueSubscriptionIds = subscriptionRepository.findDueAutoRenewSubscriptions(now).stream()
                .map(Subscription::getId)
                .toList();

        if (dueSubscriptionIds.isEmpty()) {
            return;
        }

        log.info("Found {} subscriptions due for automatic renewal", dueSubscriptionIds.size());

        for (UUID subscriptionId : dueSubscriptionIds) {
            try {
                processSingleSubscription(subscriptionId, now);
            } catch (Exception e) {
                log.error("Failed processing auto-renewal for subscription {}: {}",
                        subscriptionId, e.getMessage(), e);
            }
        }
    }

    private void processSingleSubscription(UUID subscriptionId, LocalDateTime now) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }

        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE ||
                !Boolean.TRUE.equals(subscription.getAutoRenew())) {
            return;
        }

        if (subscription.getEndDate() == null || subscription.getEndDate().isAfter(now)) {
            return;
        }

        if (!subscription.hasAutoRenewPaymentMethod()) {
            subscription.setStatus(Subscription.SubscriptionStatus.SUSPENDED);
            subscription.setAutoRenewLastFailureReason("No reusable card on file for automatic renewal");
            subscriptionRepository.save(subscription);
            log.warn("Suspended subscription {}: no reusable card token for auto-renewal", subscription.getId());
            return;
        }

        SubscriptionRenewalCoordinatorService.RenewalExecutionResult renewalResult =
                subscriptionRenewalCoordinatorService.attemptAutomaticRenewal(subscription.getId());

        if (renewalResult.isSuccess()) {
            log.info("Auto-renewal successful for subscription {} (paymentId={}, transactionId={}, invoice={})",
                    subscription.getId(), renewalResult.getPaymentId(), renewalResult.getTransactionId(),
                    renewalResult.getInvoiceNumber());
            return;
        }

        subscription.setStatus(Subscription.SubscriptionStatus.SUSPENDED);
        String failureReason = renewalResult.getMessage();
        if (renewalResult.getInvoiceNumber() != null && !renewalResult.getInvoiceNumber().isBlank()) {
            failureReason = (failureReason == null || failureReason.isBlank())
                    ? ("Renewal invoice created: " + renewalResult.getInvoiceNumber())
                    : (failureReason + " (Invoice: " + renewalResult.getInvoiceNumber() + ")");
        }
        subscription.setAutoRenewLastFailureReason(truncate(failureReason, 1000));
        subscriptionRepository.save(subscription);
        log.warn("Auto-renewal failed for subscription {}: {}",
                subscription.getId(), renewalResult.getMessage());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
