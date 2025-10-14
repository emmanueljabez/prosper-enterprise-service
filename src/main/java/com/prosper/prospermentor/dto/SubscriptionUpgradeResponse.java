package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for subscription upgrade that includes payment and proration information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpgradeResponse {

    private Subscription subscription;
    private PaymentInfo payment;
    private BigDecimal proratedAmount;
    private SubscriptionPlan newPlan;
    private String message;

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
        private String description;

        public static PaymentInfo fromPayment(Payment payment) {
            PaymentInfo info = new PaymentInfo();
            info.setPaymentId(payment.getId().toString());
            info.setCheckoutRequestId(payment.getCheckoutRequestId());
            info.setAmount(payment.getAmount().toString());
            info.setCurrency(payment.getCurrency());
            info.setPhoneNumber(payment.getPhoneNumber());
            info.setStatus(payment.getStatus().name());
            info.setDescription(payment.getDescription());
            return info;
        }
    }

    public void setPayment(Payment payment) {
        this.payment = PaymentInfo.fromPayment(payment);
    }
}
