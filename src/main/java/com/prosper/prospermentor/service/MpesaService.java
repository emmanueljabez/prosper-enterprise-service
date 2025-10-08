package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for handling Mpesa STK Push payments
 */
@Service
@Slf4j
public class MpesaService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;
    private final SessionRepository sessionRepository;

    @Value("${mpesa.consumer.key:}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret:}")
    private String consumerSecret;

    @Value("${mpesa.api.url:https://sandbox.safaricom.co.ke}")
    private String mpesaApiUrl;

    @Value("${mpesa.shortcode:174379}")
    private String shortcode;

    @Value("${mpesa.passkey:}")
    private String passkey;

    @Value("${mpesa.callback.url:}")
    private String callbackUrl;

    public MpesaService(PaymentRepository paymentRepository,
                        @Lazy SubscriptionService subscriptionService,
                        SessionRepository sessionRepository) {
        this.paymentRepository = paymentRepository;
        this.subscriptionService = subscriptionService;
        this.sessionRepository = sessionRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get Mpesa access token
     */
    public String getAccessToken() {
        try {
            String auth = consumerKey + ":" + consumerSecret;
            String encodedAuth = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> request = new HttpEntity<>(headers);

            String url = mpesaApiUrl + "/oauth/v1/generate?grant_type=client_credentials";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String accessToken = jsonNode.get("access_token").asText();
                log.info("Successfully obtained Mpesa access token");
                return accessToken;
            }

            throw new RuntimeException("Failed to get Mpesa access token");

        } catch (Exception e) {
            log.error("Error getting Mpesa access token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get Mpesa access token", e);
        }
    }

    /**
     * Initiate STK Push payment
     */
    public Payment initiateSTKPush(UUID userId, UUID sessionId, UUID subscriptionId,
                                    Payment.PaymentType paymentType, BigDecimal amount,
                                    String phoneNumber, String description) {
        try {
            log.info("Initiating STK Push for user {} amount {}", userId, amount);

            // Format phone number (remove leading 0 or +254, add 254)
            String formattedPhone = formatPhoneNumber(phoneNumber);

            // Generate timestamp
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Generate password
            String password = Base64.getEncoder().encodeToString(
                    (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

            // Create payment record
            Payment payment = new Payment();
            payment.setUserId(userId);
            payment.setPayerId(userId);
            UUID resolvedRecipientId = userId;
            if (sessionId != null) {
                resolvedRecipientId = sessionRepository.findById(sessionId)
                        .map(Session::getMentorId)
                        .orElseGet(() -> {
                            log.warn("Session {} not found when determining recipient. Falling back to userId.", sessionId);
                            return userId;
                        });
            }
            payment.setRecipientId(resolvedRecipientId);
            payment.setSessionId(sessionId);
            payment.setSubscriptionId(subscriptionId);
            payment.setPaymentType(paymentType);
            payment.setAmount(amount);
            payment.setCurrency("KES");
            payment.setPhoneNumber(formattedPhone);
            payment.setDescription(description);
            payment.setStatus(Payment.PaymentStatus.PENDING);
            payment.setPaymentMethod(Payment.PaymentMethod.MPESA);

            payment = paymentRepository.save(payment);

            // Prepare STK Push request
            Map<String, Object> stkPushRequest = new HashMap<>();
            stkPushRequest.put("BusinessShortCode", shortcode);
            stkPushRequest.put("Password", password);
            stkPushRequest.put("Timestamp", timestamp);
            stkPushRequest.put("TransactionType", "CustomerPayBillOnline");
            stkPushRequest.put("Amount", amount.intValue());
            stkPushRequest.put("PartyA", formattedPhone);
            stkPushRequest.put("PartyB", shortcode);
            stkPushRequest.put("PhoneNumber", formattedPhone);
            stkPushRequest.put("CallBackURL", callbackUrl);
            stkPushRequest.put("AccountReference", payment.getId().toString());
            stkPushRequest.put("TransactionDesc", description);

            // Get access token and make request
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(stkPushRequest, headers);

            String url = mpesaApiUrl + "/mpesa/stkpush/v1/processrequest";
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                String responseCode = jsonNode.get("ResponseCode").asText();
                if ("0".equals(responseCode)) {
                    // Success
                    String checkoutRequestId = jsonNode.get("CheckoutRequestID").asText();
                    String merchantRequestId = jsonNode.get("MerchantRequestID").asText();

                    payment.setCheckoutRequestId(checkoutRequestId);
                    payment.setMerchantRequestId(merchantRequestId);
                    payment = paymentRepository.save(payment);

                    log.info("STK Push initiated successfully. CheckoutRequestID: {}", checkoutRequestId);
                    return payment;
                } else {
                    String errorMessage = jsonNode.has("errorMessage") ?
                            jsonNode.get("errorMessage").asText() : "Unknown error";
                    payment.markAsFailed(errorMessage, Integer.parseInt(responseCode));
                    paymentRepository.save(payment);

                    throw new RuntimeException("STK Push failed: " + errorMessage);
                }
            }

            throw new RuntimeException("Failed to initiate STK Push");

        } catch (Exception e) {
            log.error("Error initiating STK Push: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate STK Push", e);
        }
    }

    /**
     * Query STK Push transaction status
     */
    public Payment queryTransactionStatus(String checkoutRequestId) {
        try {
            log.info("Querying transaction status for CheckoutRequestID: {}", checkoutRequestId);

            Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            if (payment.isCompleted()) {
                return payment;
            }

            // Generate timestamp
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            // Generate password
            String password = Base64.getEncoder().encodeToString(
                    (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));

            // Prepare query request
            Map<String, Object> queryRequest = new HashMap<>();
            queryRequest.put("BusinessShortCode", shortcode);
            queryRequest.put("Password", password);
            queryRequest.put("Timestamp", timestamp);
            queryRequest.put("CheckoutRequestID", checkoutRequestId);

            // Get access token and make request
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(queryRequest, headers);

            String url = mpesaApiUrl + "/mpesa/stkpushquery/v1/query";
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                String resultCode = jsonNode.get("ResultCode").asText();
                String resultDesc = jsonNode.get("ResultDesc").asText();

                payment.setResultCode(Integer.parseInt(resultCode));
                payment.setResultDescription(resultDesc);

                if ("0".equals(resultCode)) {
                    // Payment successful
                    payment.setStatus(Payment.PaymentStatus.COMPLETED);
                    payment.setCompletedAt(LocalDateTime.now());
                    log.info("Payment completed successfully: {}", checkoutRequestId);
                } else if (resultCode.equals("1032")) {
                    // User cancelled
                    payment.setStatus(Payment.PaymentStatus.CANCELLED);
                    log.info("Payment cancelled by user: {}", checkoutRequestId);
                } else {
                    // Payment failed
                    payment.setStatus(Payment.PaymentStatus.FAILED);
                    payment.setErrorMessage(resultDesc);
                    log.info("Payment failed: {} - {}", checkoutRequestId, resultDesc);
                }

                paymentRepository.save(payment);
            }

            return payment;

        } catch (Exception e) {
            log.error("Error querying transaction status: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query transaction status", e);
        }
    }

    /**
     * Handle Mpesa callback
     */
    public void handleCallback(String callbackData) {
        try {
            log.info("Processing Mpesa callback");

            JsonNode jsonNode = objectMapper.readTree(callbackData);
            JsonNode body = jsonNode.get("Body").get("stkCallback");

            String merchantRequestId = body.get("MerchantRequestID").asText();
            String checkoutRequestId = body.get("CheckoutRequestID").asText();
            int resultCode = body.get("ResultCode").asInt();
            String resultDesc = body.get("ResultDesc").asText();

            Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            payment.setResultCode(resultCode);
            payment.setResultDescription(resultDesc);

            if (resultCode == 0) {
                // Payment successful
                JsonNode callbackMetadata = body.get("CallbackMetadata").get("Item");

                String mpesaReceiptNumber = null;
                LocalDateTime transactionDate = null;

                for (JsonNode item : callbackMetadata) {
                    String name = item.get("Name").asText();
                    if ("MpesaReceiptNumber".equals(name)) {
                        mpesaReceiptNumber = item.get("Value").asText();
                    } else if ("TransactionDate".equals(name)) {
                        String dateStr = item.get("Value").asText();
                        transactionDate = LocalDateTime.parse(dateStr,
                                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    }
                }

                payment.markAsSuccessful(mpesaReceiptNumber, transactionDate);
                log.info("Payment completed via callback: {} - Receipt: {}",
                        checkoutRequestId, mpesaReceiptNumber);

                // Activate subscription if this is a subscription payment
                if (payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION &&
                    payment.getSubscriptionId() != null) {
                    try {
                        subscriptionService.activateSubscription(payment.getSubscriptionId());
                        log.info("Activated subscription {} after successful payment",
                            payment.getSubscriptionId());
                    } catch (Exception e) {
                        log.error("Failed to activate subscription {} after payment: {}",
                            payment.getSubscriptionId(), e.getMessage(), e);
                    }
                }

            } else {
                // Payment failed
                payment.markAsFailed(resultDesc, resultCode);
                log.info("Payment failed via callback: {} - {}", checkoutRequestId, resultDesc);

                // Cancel subscription if payment failed
                if (payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION &&
                    payment.getSubscriptionId() != null) {
                    try {
                        subscriptionService.cancelSubscriptionByPaymentFailure(payment.getSubscriptionId());
                        log.info("Cancelled subscription {} due to payment failure",
                            payment.getSubscriptionId());
                    } catch (Exception e) {
                        log.error("Failed to cancel subscription {} after payment failure: {}",
                            payment.getSubscriptionId(), e.getMessage(), e);
                    }
                }
            }

            paymentRepository.save(payment);

        } catch (Exception e) {
            log.error("Error processing Mpesa callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Format phone number for Mpesa (254XXXXXXXXX)
     */
    private String formatPhoneNumber(String phoneNumber) {
        // Remove spaces and special characters
        phoneNumber = phoneNumber.replaceAll("[\\s\\-()]", "");

        // Remove leading + or 0
        if (phoneNumber.startsWith("+254")) {
            phoneNumber = phoneNumber.substring(1);
        } else if (phoneNumber.startsWith("0")) {
            phoneNumber = "254" + phoneNumber.substring(1);
        } else if (!phoneNumber.startsWith("254")) {
            phoneNumber = "254" + phoneNumber;
        }

        return phoneNumber;
    }

    /**
     * Get payment by ID
     */
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
    }
}
