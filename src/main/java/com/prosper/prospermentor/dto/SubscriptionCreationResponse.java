package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Subscription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for subscription creation that may include payment information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCreationResponse {

    private Subscription subscription;
    private PaymentInfo payment;
    private boolean requiresPayment;

    /**
     * Nested class for payment information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private String paymentId;
        private String checkoutRequestId;
        private String amount;
        private String currency;
        private String phoneNumber;
        private String status;
        private String message;

        public static PaymentInfo fromPayment(Payment payment) {
            PaymentInfo info = new PaymentInfo();
            info.setPaymentId(payment.getId().toString());
            info.setCheckoutRequestId(payment.getCheckoutRequestId());
            info.setAmount(payment.getAmount().toString());
            info.setCurrency(payment.getCurrency());
            info.setPhoneNumber(payment.getPhoneNumber());
            info.setStatus(payment.getStatus().name());
            info.setMessage("Please complete payment on your phone");
            return info;
        }
    }

    /**
     * Create response for free subscription (no payment required)
     */
    public static SubscriptionCreationResponse forFreeSubscription(Subscription subscription) {
        return new SubscriptionCreationResponse(subscription, null, false);
    }

    /**
     * Create response for paid subscription (payment required)
     */
    public static SubscriptionCreationResponse forPaidSubscription(Subscription subscription, Payment payment) {
        return new SubscriptionCreationResponse(
            subscription,
            PaymentInfo.fromPayment(payment),
            true
        );
    }
}
