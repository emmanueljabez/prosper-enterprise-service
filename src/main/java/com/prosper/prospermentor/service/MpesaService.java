package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.MpesaCallbackDTO;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Profile;
import com.prosper.prospermentor.entity.Session;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.repository.ProfileRepository;
import com.prosper.prospermentor.repository.SessionRepository;
import com.prosper.prospermentor.repository.SubscriptionRepository;
import com.prosper.prospermentor.util.MpesaAccountReferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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
    private final EmailService emailService;
    private final ProfileRepository profileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final SpringTemplateEngine templateEngine;
    private final CompanySubscriptionService companySubscriptionService;

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

    @Value("${app.name:ProsperMentor}")
    private String appName;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public MpesaService(PaymentRepository paymentRepository,
                        @Lazy SubscriptionService subscriptionService,
                        SessionRepository sessionRepository,
                        EmailService emailService,
                        ProfileRepository profileRepository,
                        SubscriptionRepository subscriptionRepository,
                        InvoiceRepository invoiceRepository,
                        SpringTemplateEngine templateEngine,
                        @Lazy CompanySubscriptionService companySubscriptionService) {
        this.paymentRepository = paymentRepository;
        this.subscriptionService = subscriptionService;
        this.sessionRepository = sessionRepository;
        this.emailService = emailService;
        this.profileRepository = profileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoiceRepository = invoiceRepository;
        this.templateEngine = templateEngine;
        this.companySubscriptionService = companySubscriptionService;
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
            String accountReference = MpesaAccountReferences.forPayment(payment.getId());
            payment.setGatewayReference(accountReference);
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
            String resolvedCallbackUrl = resolveMpesaCallbackUrl();
            stkPushRequest.put("CallBackURL", resolvedCallbackUrl);
            stkPushRequest.put("AccountReference", accountReference);
            stkPushRequest.put("TransactionDesc", description);

            log.info("Using Mpesa callback URL for payment {}: {}", payment.getId(), resolvedCallbackUrl);

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

    private String resolveMpesaCallbackUrl() {
        String configured = callbackUrl == null ? "" : callbackUrl.trim();
        if (configured.isBlank()) {
            configured = trimTrailingSlash(baseUrl) + "/api/v1/payments/confirmc2b";
        }

        URI uri;
        try {
            uri = URI.create(configured);
        } catch (Exception ex) {
            throw new IllegalStateException("mpesa.callback.url is invalid: " + configured);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new IllegalStateException("mpesa.callback.url must be an absolute URL: " + configured);
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || "127.0.0.1".equals(normalizedHost)) {
            throw new IllegalStateException(
                    "mpesa.callback.url cannot use localhost/127.0.0.1 for STK. Use a public HTTPS URL.");
        }

        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("mpesa.callback.url must use HTTPS: " + configured);
        }

        return configured;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
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

            Payment.PaymentStatus previousStatus = payment.getStatus();

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

                if (previousStatus != payment.getStatus()) {
                    if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                        applyPostPaymentActions(payment, true);
                    } else if (payment.getStatus() == Payment.PaymentStatus.FAILED ||
                            payment.getStatus() == Payment.PaymentStatus.CANCELLED) {
                        applyPostPaymentActions(payment, false);
                    }
                }
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
    public void handleCallback(MpesaCallbackDTO callback) {
        try {
            log.info("Processing Mpesa callback - CheckoutRequestID: {}, MerchantRequestID: {}, ResultCode: {}, ResultDesc: {}",
                    callback.getCheckoutRequestId(), callback.getMerchantRequestId(),
                    callback.getResultCode(), callback.getResultDesc());

            String checkoutRequestId = callback.getCheckoutRequestId();
            if (checkoutRequestId == null) {
                throw new RuntimeException("CheckoutRequestID is missing in callback");
            }

            Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for CheckoutRequestID: " + checkoutRequestId));

            log.info("Found payment {} for CheckoutRequestID: {}, PaymentType: {}, SessionId: {}, SubscriptionId: {}",
                    payment.getId(), checkoutRequestId, payment.getPaymentType(),
                    payment.getSessionId(), payment.getSubscriptionId());

            // Update payment with callback data
            payment.setMerchantRequestId(callback.getMerchantRequestId());
            payment.setResultCode(callback.getResultCode());
            payment.setResultDescription(callback.getResultDesc());

            if (callback.isSuccessful()) {
                // Payment successful - extract metadata
                String mpesaReceiptNumber = callback.getMpesaReceiptNumber();
                String transactionDateStr = callback.getTransactionDate();
                BigDecimal amount = callback.getAmount();
                String phoneNumber = callback.getPhoneNumber();

                // Parse transaction date
                LocalDateTime transactionDate = null;
                if (transactionDateStr != null) {
                    try {
                        transactionDate = LocalDateTime.parse(transactionDateStr,
                                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    } catch (Exception e) {
                        log.warn("Failed to parse transaction date: {}", transactionDateStr, e);
                    }
                }

                payment.markAsSuccessful(mpesaReceiptNumber, transactionDate);
                log.info("Payment completed via callback: {} - Receipt: {}, Amount: {}, Phone: {}",
                        checkoutRequestId, mpesaReceiptNumber, amount, phoneNumber);
                applyPostPaymentActions(payment, true);

            } else {
                // Payment failed
                payment.markAsFailed(callback.getResultDesc(), callback.getResultCode());
                log.info("Payment failed via callback: {} - ResultCode: {}, ResultDesc: {}",
                        checkoutRequestId, callback.getResultCode(), callback.getResultDesc());
                applyPostPaymentActions(payment, false);
            }

            paymentRepository.save(payment);
            log.info("Payment {} saved successfully with status: {}", payment.getId(), payment.getStatus());

        } catch (Exception e) {
            log.error("Error processing Mpesa callback: {}", e.getMessage(), e);
            throw new RuntimeException("Callback processing failed", e);
        }
    }

    /**
     * Applies payment-type side effects after gateway status is known.
     * Shared by Mpesa and card callbacks to keep behavior aligned.
     */
    public void applyPostPaymentActions(Payment payment, boolean successful) {
        if (successful) {
            if (payment.getPaymentType() == Payment.PaymentType.INVOICE &&
                    payment.getInvoiceId() != null) {
                handleInvoicePaymentSuccess(payment);
            }

            if (payment.getPaymentType() == Payment.PaymentType.SESSION_BOOKING &&
                    payment.getSessionId() != null) {
                handleSessionPaymentSuccess(payment);
            }

            if (payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION &&
                    payment.getSubscriptionId() != null) {
                handleSubscriptionPaymentSuccess(payment);
            }

            if (payment.getPaymentType() == Payment.PaymentType.UPGRADE &&
                    payment.getSubscriptionId() != null) {
                handleUpgradePaymentSuccess(payment);
            }

            if (payment.getPaymentType() == Payment.PaymentType.ADDON ||
                    payment.getPaymentType() == Payment.PaymentType.TOP_UP) {
                handleGenericPaymentSuccess(payment);
            }

            // Send payment receipt email asynchronously
            sendPaymentReceipt(payment);
            return;
        }

        if (payment.getPaymentType() == Payment.PaymentType.SESSION_BOOKING &&
                payment.getSessionId() != null) {
            handleSessionPaymentFailure(payment);
        }

        if (payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION &&
                payment.getSubscriptionId() != null) {
            handleSubscriptionPaymentFailure(payment);
        }

        if (payment.getPaymentType() == Payment.PaymentType.UPGRADE &&
                payment.getSubscriptionId() != null) {
            handleUpgradePaymentFailure(payment);
        }

        if (payment.getPaymentType() == Payment.PaymentType.ADDON ||
                payment.getPaymentType() == Payment.PaymentType.TOP_UP) {
            handleGenericPaymentFailure(payment);
        }

        if (payment.getPaymentType() == Payment.PaymentType.INVOICE &&
                payment.getInvoiceId() != null) {
            handleInvoicePaymentFailure(payment);
        }
    }

    private void handleInvoicePaymentSuccess(Payment payment) {
        try {
            Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                    .orElseThrow(() -> new RuntimeException("Invoice not found: " + payment.getInvoiceId()));

            if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
                return;
            }

            applyInvoiceBusinessActions(invoice, payment);
            invoice.markAsPaid();
            invoiceRepository.save(invoice);
            log.info("Invoice {} marked as PAID after successful payment {}", invoice.getId(), payment.getId());
        } catch (Exception e) {
            log.error("Failed to mark invoice {} as paid after payment {}: {}",
                    payment.getInvoiceId(), payment.getId(), e.getMessage(), e);
        }
    }

    private void handleInvoicePaymentFailure(Payment payment) {
        // Keep invoice OPEN on failed/cancelled attempts so the payer can retry.
        log.info("Invoice payment {} failed/cancelled with status {}. Invoice {} remains payable.",
                payment.getId(), payment.getStatus(), payment.getInvoiceId());
    }

    private void applyInvoiceBusinessActions(Invoice invoice, Payment payment) {
        String rawMetadata = invoice.getMetadata();
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return;
        }

        try {
            JsonNode metadata = objectMapper.readTree(rawMetadata);
            String context = readMetadataText(metadata, "invoiceContext", "context", "paymentContext");
            if (context == null || context.isBlank()) {
                return;
            }

            String normalizedContext = context.trim().toUpperCase(Locale.ROOT);

            if ("PLAN_PURCHASE".equals(normalizedContext) ||
                    "PLAN_UPGRADE".equals(normalizedContext) ||
                    "SUBSCRIPTION_PLAN".equals(normalizedContext)) {
                UUID planId = readMetadataUuid(metadata, "planId");
                UUID userId = firstNonNull(payment.getUserId(), invoice.getPayerUserId());
                BillingInterval billingInterval = BillingInterval.fromString(
                        readMetadataText(metadata, "billingInterval", "planBillingInterval")
                );

                if (planId == null || userId == null) {
                    log.warn("Invoice {} missing planId/userId for plan fulfillment", invoice.getId());
                    return;
                }

                var result = subscriptionService.applyPlanInvoicePayment(userId, planId, billingInterval);
                if (!result.isSuccess()) {
                    log.warn("Invoice {} plan fulfillment warning: {}", invoice.getId(), result.getMessage());
                }
                return;
            }

            if ("SESSION_ADDON".equals(normalizedContext) ||
                    "ADDON_PURCHASE".equals(normalizedContext)) {
                Integer quantity = readMetadataInt(metadata, "quantity", 1);
                UUID userId = firstNonNull(payment.getUserId(), invoice.getPayerUserId());

                if (userId == null) {
                    log.warn("Invoice {} missing userId for add-on fulfillment", invoice.getId());
                    return;
                }

                var result = subscriptionService.applyAddonInvoicePayment(
                        userId,
                        quantity != null && quantity > 0 ? quantity : 1,
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getId()
                );
                if (!result.isSuccess()) {
                    log.warn("Invoice {} add-on fulfillment warning: {}", invoice.getId(), result.getMessage());
                }
                return;
            }

            if ("SUBSCRIPTION_RENEWAL".equals(normalizedContext)) {
                UUID subscriptionId = readMetadataUuid(metadata, "subscriptionId");
                UUID userId = firstNonNull(payment.getUserId(), invoice.getPayerUserId());
                String renewalMode = readMetadataText(metadata, "renewalMode");

                if (subscriptionId == null) {
                    log.warn("Invoice {} missing subscriptionId for renewal fulfillment", invoice.getId());
                    return;
                }

                var result = subscriptionService.applyRenewalInvoicePayment(userId, subscriptionId, renewalMode);
                if (!result.isSuccess()) {
                    log.warn("Invoice {} renewal fulfillment warning: {}", invoice.getId(), result.getMessage());
                }
                return;
            }

            if ("COMPANY_SUBSCRIPTION_PURCHASE".equals(normalizedContext)
                    || "COMPANY_SUBSCRIPTION_CHANGE".equals(normalizedContext)
                    || "COMPANY_SUBSCRIPTION_RENEWAL".equals(normalizedContext)) {
                UUID companySubscriptionId = readMetadataUuid(metadata, "companySubscriptionId");
                UUID targetPlanId = readMetadataUuid(metadata, "targetPlanId");
                Integer targetSeatCount = readMetadataInt(metadata, "targetSeatCount", readMetadataInt(metadata, "seatCount", null));
                Integer targetSessionCount = readMetadataInt(metadata, "targetSessionCount", readMetadataInt(metadata, "sessionCount", targetSeatCount));
                BillingInterval billingInterval = BillingInterval.fromString(
                        readMetadataText(metadata, "billingInterval", "planBillingInterval")
                );
                if (companySubscriptionId == null) {
                    log.warn("Invoice {} missing companySubscriptionId for corporate fulfillment", invoice.getId());
                    return;
                }

                companySubscriptionService.applyInvoicePayment(
                        companySubscriptionId,
                        normalizedContext,
                        billingInterval,
                        targetPlanId,
                        targetSeatCount,
                        targetSessionCount
                );
                return;
            }

            log.info("Invoice {} has no mapped fulfillment action for context '{}'", invoice.getId(), normalizedContext);
        } catch (Exception e) {
            log.error("Failed to apply invoice business actions for invoice {}: {}", invoice.getId(), e.getMessage(), e);
        }
    }

    private String readMetadataText(JsonNode metadata, String... fieldNames) {
        if (metadata == null || fieldNames == null) {
            return null;
        }

        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            JsonNode node = metadata.get(fieldName);
            if (node != null && !node.isNull()) {
                String value = node.asText(null);
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private UUID readMetadataUuid(JsonNode metadata, String fieldName) {
        String rawValue = readMetadataText(metadata, fieldName);
        if (rawValue == null) {
            return null;
        }

        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid UUID metadata for field {}: {}", fieldName, rawValue);
            return null;
        }
    }

    private Integer readMetadataInt(JsonNode metadata, String fieldName, Integer defaultValue) {
        if (metadata == null || fieldName == null || fieldName.isBlank()) {
            return defaultValue;
        }

        JsonNode node = metadata.get(fieldName);
        if (node == null || node.isNull()) {
            return defaultValue;
        }

        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }

        String rawValue = node.asText(null);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid integer metadata for field {}: {}", fieldName, rawValue);
            return defaultValue;
        }
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    /**
     * Handle successful session booking payment
     */
    private void handleSessionPaymentSuccess(Payment payment) {
        try {
            Session session = sessionRepository.findById(payment.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Session not found: " + payment.getSessionId()));

            log.info("Updating session {} after successful payment. Current status: {}, paymentStatus: {}",
                    session.getId(), session.getStatus(), session.getPaymentStatus());

            // Update session payment status
            session.setPaymentStatus(Session.PaymentStatus.PAID);
            session.setPaid(true);

            log.info("Session {} marked paid after successful payment; status remains {} pending mentor review",
                    session.getId(), session.getStatus());

            sessionRepository.save(session);
            log.info("Session {} updated successfully - Status: {}, PaymentStatus: {}, Paid: {}",
                    session.getId(), session.getStatus(), session.getPaymentStatus(), session.getPaid());
        } catch (Exception e) {
            log.error("Failed to update session {} after payment: {}",
                    payment.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Handle failed session booking payment
     */
    private void handleSessionPaymentFailure(Payment payment) {
        try {
            Session session = sessionRepository.findById(payment.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Session not found: " + payment.getSessionId()));

            session.setPaymentStatus(Session.PaymentStatus.FAILED);
            sessionRepository.save(session);
            log.info("Session {} payment marked as failed", session.getId());
        } catch (Exception e) {
            log.error("Failed to update session {} after payment failure: {}",
                    payment.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Handle successful subscription payment
     */
    private void handleSubscriptionPaymentSuccess(Payment payment) {
        try {
            subscriptionService.activateSubscription(payment.getSubscriptionId());
            log.info("Activated subscription {} after successful payment",
                    payment.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to activate subscription {} after payment: {}",
                    payment.getSubscriptionId(), e.getMessage(), e);
        }
    }

    /**
     * Handle failed subscription payment
     */
    private void handleSubscriptionPaymentFailure(Payment payment) {
        try {
            subscriptionService.cancelSubscriptionByPaymentFailure(payment.getSubscriptionId());
            log.info("Cancelled subscription {} due to payment failure",
                    payment.getSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to cancel subscription {} after payment failure: {}",
                    payment.getSubscriptionId(), e.getMessage(), e);
        }
    }

    /**
     * Handle successful subscription upgrade payment
     */
    private void handleUpgradePaymentSuccess(Payment payment) {
        try {
            // Extract target plan ID from payment metadata
            String metadata = payment.getMetadata();
            if (metadata == null || metadata.isEmpty()) {
                log.error("No metadata found for upgrade payment {}", payment.getId());
                return;
            }

            // Parse metadata JSON to get target plan ID
            UUID targetPlanId = extractTargetPlanIdFromMetadata(metadata);
            BillingInterval billingInterval = BillingInterval.fromString(extractMetadataField(metadata, "billingInterval"));
            if (targetPlanId == null) {
                log.error("Could not extract target plan ID from metadata for payment {}", payment.getId());
                return;
            }

            // Complete the subscription upgrade
            subscriptionService.completeSubscriptionUpgrade(payment.getSubscriptionId(), targetPlanId, billingInterval);
            log.info("Completed subscription upgrade {} to plan {} after successful payment",
                    payment.getSubscriptionId(), targetPlanId);
        } catch (Exception e) {
            log.error("Failed to complete subscription upgrade {} after payment: {}",
                    payment.getSubscriptionId(), e.getMessage(), e);
        }
    }

    /**
     * Handle failed subscription upgrade payment
     */
    private void handleUpgradePaymentFailure(Payment payment) {
        try {
            log.info("Subscription upgrade payment failed for subscription {}. Subscription remains on current plan.",
                    payment.getSubscriptionId());
            // No action needed - subscription stays on current plan
        } catch (Exception e) {
            log.error("Error handling failed upgrade payment for subscription {}: {}",
                    payment.getSubscriptionId(), e.getMessage(), e);
        }
    }

    /**
     * Extract target plan ID from payment metadata JSON
     */
    private UUID extractTargetPlanIdFromMetadata(String metadata) {
        try {
            String planIdStr = extractMetadataField(metadata, "targetPlanId");
            if (planIdStr != null) {
                return UUID.fromString(planIdStr);
            }
        } catch (Exception e) {
            log.error("Failed to parse target plan ID from metadata: {}", metadata, e);
        }
        return null;
    }

    private String extractMetadataField(String metadata, String fieldName) {
        try {
            if (metadata == null || metadata.isBlank() || fieldName == null || fieldName.isBlank()) {
                return null;
            }
            String token = "\"" + fieldName + "\":\"";
            int startIdx = metadata.indexOf(token);
            if (startIdx < 0) {
                return null;
            }
            startIdx += token.length();
            int endIdx = metadata.indexOf("\"", startIdx);
            if (endIdx <= startIdx) {
                return null;
            }
            return metadata.substring(startIdx, endIdx);
        } catch (Exception e) {
            log.error("Failed to parse {} from metadata: {}", fieldName, metadata, e);
            return null;
        }
    }

    /**
     * Handle successful generic payment (events, top-ups, add-ons, etc.)
     */
    private void handleGenericPaymentSuccess(Payment payment) {
        try {
            log.info("Processing successful generic payment {} of type {}",
                    payment.getId(), payment.getPaymentType());

            // For generic payments, we just log the success
            // Additional business logic can be added here based on payment type or metadata
            // For example, if metadata contains event details, you could:
            // - Update event attendance records
            // - Send event confirmation emails
            // - Update user account balance (for top-ups)

            String metadata = payment.getMetadata();
            if (metadata != null && !metadata.isEmpty()) {
                log.info("Payment {} completed with metadata: {}", payment.getId(), metadata);
            }

        } catch (Exception e) {
            log.error("Error handling generic payment success for payment {}: {}",
                    payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Handle failed generic payment
     */
    private void handleGenericPaymentFailure(Payment payment) {
        try {
            log.info("Processing failed generic payment {} of type {}",
                    payment.getId(), payment.getPaymentType());

            // For generic payments, we just log the failure
            // Additional business logic can be added here based on payment type or metadata

        } catch (Exception e) {
            log.error("Error handling generic payment failure for payment {}: {}",
                    payment.getId(), e.getMessage(), e);
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

    /**
     * Update payment metadata
     */
    public Payment updatePaymentMetadata(UUID paymentId, String metadata) {
        Payment payment = getPaymentById(paymentId);
        payment.setMetadata(metadata);
        return paymentRepository.save(payment);
    }

    /**
     * Send payment receipt email asynchronously
     */
    @Async
    public void sendPaymentReceipt(Payment payment) {
        try {
            log.info("Preparing to send payment receipt for payment {}", payment.getId());

            // Get user profile
            Optional<Profile> profileOpt = profileRepository.findById(payment.getUserId());
            if (profileOpt.isEmpty()) {
                log.error("Profile not found for user ID: {}", payment.getUserId());
                return;
            }

            Profile profile = profileOpt.get();
            String recipientEmail = profile.getEmail();

            if (recipientEmail == null || recipientEmail.isEmpty()) {
                log.error("No email address found for user ID: {}", payment.getUserId());
                return;
            }

            // Generate receipt HTML
            String receiptHtml = generateReceiptHtml(payment, profile);

            // Send email
            String referenceForSubject = getTransactionReference(payment);
            if (referenceForSubject == null || referenceForSubject.isBlank()) {
                referenceForSubject = payment.getId().toString();
            }
            String subject = String.format("Payment Receipt - %s", referenceForSubject);
            emailService.sendEmail(recipientEmail, subject, receiptHtml, new ArrayList<>());

            log.info("Payment receipt sent successfully to {} for payment {}", recipientEmail, payment.getId());

        } catch (Exception e) {
            log.error("Failed to send payment receipt for payment {}: {}",
                    payment.getId(), e.getMessage(), e);
        }
    }

    /**
     * Generate HTML receipt email content using Thymeleaf template
     */
    private String generateReceiptHtml(Payment payment, Profile profile) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss");
        DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        DateTimeFormatter validityFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");

        // Build customer name
        String customerName = buildCustomerName(profile);

        // Format payment date
        String paymentDate = payment.getTransactionDate() != null
            ? payment.getTransactionDate().format(dateFormatter)
            : payment.getCompletedAt() != null
                ? payment.getCompletedAt().format(dateFormatter)
                : payment.getCreatedAt().format(dateFormatter);

        // Get payment type description
        String paymentType = getPaymentDescription(payment);
        String paymentMethodDisplay = getPaymentMethodDisplay(payment);
        String transactionReference = getTransactionReference(payment);

        // Create Thymeleaf context
        Context context = new Context();
        context.setVariable("appName", appName);
        context.setVariable("baseUrl", baseUrl);
        context.setVariable("payment", payment);
        context.setVariable("profile", profile);
        context.setVariable("customerName", customerName);
        context.setVariable("paymentDate", paymentDate);
        context.setVariable("paymentType", paymentType);
        context.setVariable("paymentMethodDisplay", paymentMethodDisplay);
        context.setVariable("transactionReference", transactionReference != null ? transactionReference : "N/A");

        // Add item details based on payment type
        boolean hasItemDetails = false;
        if (payment.getPaymentType() == Payment.PaymentType.SESSION_BOOKING && payment.getSessionId() != null) {
            Optional<Session> sessionOpt = sessionRepository.findById(payment.getSessionId());
            if (sessionOpt.isPresent()) {
                Session session = sessionOpt.get();
                context.setVariable("session", session);
                context.setVariable("sessionDate", session.getScheduledStart() != null
                    ? session.getScheduledStart().format(shortDateFormatter)
                    : "To be scheduled");
                context.setVariable("sessionDuration", session.getDurationMinutes());
                hasItemDetails = true;
            }
        } else if ((payment.getPaymentType() == Payment.PaymentType.SUBSCRIPTION ||
                    payment.getPaymentType() == Payment.PaymentType.UPGRADE) &&
                   payment.getSubscriptionId() != null) {
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findById(payment.getSubscriptionId());
            if (subscriptionOpt.isPresent()) {
                Subscription subscription = subscriptionOpt.get();
                context.setVariable("subscription", subscription);
                context.setVariable("validUntil", subscription.getEndDate().format(validityFormatter));
                hasItemDetails = true;
            }
        }

        context.setVariable("itemDetails", hasItemDetails ? "present" : null);

        // Render template
        return templateEngine.process("email/payment-receipt", context);
    }

    /**
     * Build customer name from profile
     */
    private String buildCustomerName(Profile profile) {
        if (profile.getFirstName() != null && profile.getLastName() != null) {
            return profile.getFirstName() + " " + profile.getLastName();
        } else if (profile.getFirstName() != null) {
            return profile.getFirstName();
        } else if (profile.getLastName() != null) {
            return profile.getLastName();
        } else {
            return "Valued Customer";
        }
    }

    /**
     * Get payment description based on type
     */
    private String getPaymentDescription(Payment payment) {
        return switch (payment.getPaymentType()) {
            case SESSION_BOOKING -> "Session Booking";
            case SUBSCRIPTION -> "Subscription Payment";
            case UPGRADE -> "Subscription Upgrade";
            case ADDON -> "Add-on Purchase";
            case TOP_UP -> "Account Top-up";
            case REFUND -> "Refund";
            case INVOICE -> "Invoice Payment";
        };
    }

    private String getPaymentMethodDisplay(Payment payment) {
        Payment.PaymentMethod paymentMethod = payment.getPaymentMethod();
        if (paymentMethod == null) {
            return "Unknown";
        }

        return switch (paymentMethod) {
            case MPESA -> "M-Pesa";
            case CARD -> {
                String lastFour = payment.getCardLastFour();
                if (lastFour != null && !lastFour.isBlank()) {
                    yield "Card (**** " + lastFour + ")";
                }
                yield "Card";
            }
            case BANK_TRANSFER -> "Bank Transfer";
            case PAYPAL -> "PayPal";
            case FREE -> "Free";
        };
    }

    private String getTransactionReference(Payment payment) {
        if (payment.getPaymentMethod() == Payment.PaymentMethod.CARD) {
            return firstNonBlank(
                    payment.getGatewayTransactionId(),
                    payment.getGatewayReference(),
                    payment.getGatewayAuthCode(),
                    payment.getId() != null ? payment.getId().toString() : null
            );
        }

        if (payment.getPaymentMethod() == Payment.PaymentMethod.MPESA) {
            return firstNonBlank(
                    payment.getMpesaReceiptNumber(),
                    payment.getCheckoutRequestId(),
                    payment.getId() != null ? payment.getId().toString() : null
            );
        }

        return firstNonBlank(
                payment.getGatewayTransactionId(),
                payment.getMpesaReceiptNumber(),
                payment.getId() != null ? payment.getId().toString() : null
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }
}
