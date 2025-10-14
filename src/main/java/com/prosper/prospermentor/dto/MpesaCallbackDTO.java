package com.prosper.prospermentor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for M-Pesa STK Push callback payload
 * Represents the complete callback structure from Safaricom
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MpesaCallbackDTO {

    @JsonProperty("Body")
    private Body body;

    /**
     * Body wrapper containing the STK callback
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }

    /**
     * STK Callback details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StkCallback {
        @JsonProperty("MerchantRequestID")
        private String merchantRequestId;

        @JsonProperty("CheckoutRequestID")
        private String checkoutRequestId;

        @JsonProperty("ResultCode")
        private Integer resultCode;

        @JsonProperty("ResultDesc")
        private String resultDesc;

        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;
    }

    /**
     * Callback metadata containing transaction details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallbackMetadata {
        @JsonProperty("Item")
        private List<Item> items;
    }

    /**
     * Individual metadata item (Amount, MpesaReceiptNumber, etc.)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("Name")
        private String name;

        @JsonProperty("Value")
        private Object value;
    }

    /**
     * Helper method to extract a specific metadata value by name
     */
    public Object getMetadataValue(String name) {
        if (body != null && body.stkCallback != null &&
            body.stkCallback.callbackMetadata != null &&
            body.stkCallback.callbackMetadata.items != null) {

            return body.stkCallback.callbackMetadata.items.stream()
                    .filter(item -> name.equals(item.getName()))
                    .map(Item::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Helper method to get MpesaReceiptNumber from metadata
     */
    public String getMpesaReceiptNumber() {
        Object value = getMetadataValue("MpesaReceiptNumber");
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to get Amount from metadata
     */
    public BigDecimal getAmount() {
        Object value = getMetadataValue("Amount");
        if (value != null) {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper method to get TransactionDate from metadata
     */
    public String getTransactionDate() {
        Object value = getMetadataValue("TransactionDate");
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to get PhoneNumber from metadata
     */
    public String getPhoneNumber() {
        Object value = getMetadataValue("PhoneNumber");
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to get Balance from metadata
     */
    public BigDecimal getBalance() {
        Object value = getMetadataValue("Balance");
        if (value != null) {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Check if the callback represents a successful transaction
     */
    public boolean isSuccessful() {
        return body != null &&
               body.stkCallback != null &&
               body.stkCallback.resultCode != null &&
               body.stkCallback.resultCode == 0;
    }

    /**
     * Get the CheckoutRequestID from the callback
     */
    public String getCheckoutRequestId() {
        return body != null && body.stkCallback != null ?
               body.stkCallback.checkoutRequestId : null;
    }

    /**
     * Get the MerchantRequestID from the callback
     */
    public String getMerchantRequestId() {
        return body != null && body.stkCallback != null ?
               body.stkCallback.merchantRequestId : null;
    }

    /**
     * Get the ResultCode from the callback
     */
    public Integer getResultCode() {
        return body != null && body.stkCallback != null ?
               body.stkCallback.resultCode : null;
    }

    /**
     * Get the ResultDesc from the callback
     */
    public String getResultDesc() {
        return body != null && body.stkCallback != null ?
               body.stkCallback.resultDesc : null;
    }
}
