package com.prosper.prospermentor.dto;

import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.SubscriptionAddon;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for addon purchase that includes payment information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAddonResponse {

    private SubscriptionAddon addon;
    private PaymentInfo payment;

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
     * Create response from addon and payment
     */
    public static PurchaseAddonResponse of(SubscriptionAddon addon, Payment payment) {
        return new PurchaseAddonResponse(
            addon,
            PaymentInfo.fromPayment(payment)
        );
    }
}
