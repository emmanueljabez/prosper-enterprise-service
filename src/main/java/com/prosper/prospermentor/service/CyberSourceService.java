package com.prosper.prospermentor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.CyberSourceTransaction;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.repository.CyberSourceTransactionRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for handling CyberSource Secure Acceptance Hosted Checkout payments
 * Implements signature generation, payment initiation, and callback handling
 */
@Service
@Slf4j
public class CyberSourceService {

    private static final Set<String> RECURRING_SUCCESS_STATUSES = Set.of(
            "AUTHORIZED",
            "PENDING",
            "TRANSMITTED",
            "COMPLETED"
    );

    private final PaymentRepository paymentRepository;
    private final CyberSourceTransactionRepository cyberSourceTransactionRepository;
    private final MpesaService mpesaService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${cybersource.merchant.id}")
    private String merchantId;

    @Value("${cybersource.access.key}")
    private String accessKey;

    @Value("${cybersource.secret.key}")
    private String secretKey;

    @Value("${cybersource.profile.id}")
    private String profileId;

    @Value("${cybersource.endpoint.url}")
    private String endpointUrl;

    @Value("${cybersource.callback.url}")
    private String callbackUrl;

    @Value("${cybersource.return.url}")
    private String returnUrl;

    @Value("${cybersource.cancel.url}")
    private String cancelUrl;

    @Value("${cybersource.recurring.enabled:false}")
    private boolean recurringEnabled;

    @Value("${cybersource.rest.api.base-url:https://apitest.cybersource.com}")
    private String restApiBaseUrl;

    @Value("${app.name:ProsperMentor}")
    private String appName;

    public CyberSourceService(PaymentRepository paymentRepository,
                             CyberSourceTransactionRepository cyberSourceTransactionRepository,
                             @Lazy MpesaService mpesaService,
                             @Lazy SubscriptionService subscriptionService,
                             ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.cyberSourceTransactionRepository = cyberSourceTransactionRepository;
        this.mpesaService = mpesaService;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Generate CyberSource payment parameters with HMAC signature
     *
     * @param payment The payment entity for which to generate parameters
     * @return Map of parameter name to value for CyberSource form submission
     */
    public Map<String, String> generatePaymentParameters(Payment payment) {
        return generatePaymentParameters(payment, null, null);
    }

    /**
     * Generate CyberSource payment parameters with optional per-payment return/cancel URLs.
     */
    public Map<String, String> generatePaymentParameters(Payment payment, String customReturnUrl, String customCancelUrl) {
        try {
            log.info("Generating CyberSource payment parameters for payment ID: {}", payment.getId());

            // Generate unique transaction UUID
            String transactionUuid = UUID.randomUUID().toString();
            String referenceNumber = "PAY-" + payment.getId().toString().substring(0, 8).toUpperCase();

            // Generate timestamp in UTC (CyberSource requirement)
            String signedDateTime = LocalDateTime.now(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

            // Build parameters map in order (important for signature)
            Map<String, String> params = new LinkedHashMap<>();

            // Core merchant parameters
            params.put("access_key", accessKey);
            params.put("profile_id", profileId);
            params.put("transaction_uuid", transactionUuid);
            params.put("signed_field_names", buildSignedFieldNames());
            params.put("unsigned_field_names", "");
            params.put("signed_date_time", signedDateTime);
            params.put("locale", "en");

            // Transaction parameters
            params.put("transaction_type", "sale");
            params.put("reference_number", referenceNumber);
            params.put("amount", payment.getAmount().toPlainString());
            params.put("currency", payment.getCurrency());
            params.put("payment_method", "card");

            // Return URLs for callback handling
            String resolvedReturnUrl = firstNonBlank(customReturnUrl, returnUrl);
            String resolvedCancelUrl = firstNonBlank(customCancelUrl, cancelUrl);
            params.put("override_custom_receipt_page", resolvedReturnUrl);
            params.put("override_custom_cancel_page", resolvedCancelUrl);

            // Merchant defined data for tracking (store payment context)
            // IMPORTANT: All merchant_defined_data fields MUST be present if they're in signed_field_names
            params.put("merchant_defined_data1", payment.getUserId().toString());
            params.put("merchant_defined_data2", payment.getPaymentType().toString());

            // Store session ID or subscription ID depending on payment type
            if (payment.getSessionId() != null) {
                params.put("merchant_defined_data3", payment.getSessionId().toString());
            } else if (payment.getSubscriptionId() != null) {
                params.put("merchant_defined_data3", payment.getSubscriptionId().toString());
            } else {
                // Include empty string to match signed_field_names
                params.put("merchant_defined_data3", "");
            }

            params.put("merchant_defined_data4", payment.getId().toString());

            // Log parameters before signature (for debugging)
            log.debug("CyberSource parameters before signature:");
            params.forEach((key, value) -> {
                if (key.equals("access_key") || key.equals("profile_id")) {
                    log.debug("  {} = {}...{}", key, value.substring(0, Math.min(8, value.length())),
                            value.length() > 8 ? value.substring(value.length() - 4) : "");
                } else {
                    log.debug("  {} = {}", key, value);
                }
            });

            // Generate HMAC-SHA256 signature
            String signature = generateSignature(params);
            params.put("signature", signature);

            log.info("✅ Generated CyberSource signature: {}...{}",
                    signature.substring(0, Math.min(12, signature.length())),
                    signature.length() > 12 ? signature.substring(signature.length() - 4) : "");

            // Save transaction record for tracking
            saveCyberSourceTransaction(payment, transactionUuid, referenceNumber, params);

            log.info("Successfully generated CyberSource parameters for payment {}", payment.getId());
            return params;

        } catch (Exception e) {
            log.error("Error generating CyberSource payment parameters: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate payment parameters", e);
        }
    }

    /**
     * Generate HMAC-SHA256 signature for CyberSource request
     * This signature ensures request integrity and authenticity
     *
     * @param params Map of parameters to sign
     * @return Base64-encoded signature
     */
    public String generateSignature(Map<String, String> params) {
        try {
            // Get list of fields to sign
            String signedFieldNamesStr = params.get("signed_field_names");
            if (signedFieldNamesStr == null || signedFieldNamesStr.isEmpty()) {
                throw new IllegalArgumentException("signed_field_names is required");
            }

            String[] signedFieldNames = signedFieldNamesStr.split(",");

            // Build data to sign: field1=value1,field2=value2,...
            StringBuilder dataToSign = new StringBuilder();
            for (int i = 0; i < signedFieldNames.length; i++) {
                String fieldName = signedFieldNames[i].trim();
                String fieldValue = params.get(fieldName);

                if (fieldValue == null) {
                    log.warn("⚠️ Field '{}' in signed_field_names but not found in params, using empty string", fieldName);
                    fieldValue = "";
                }

                dataToSign.append(fieldName).append("=").append(fieldValue);

                if (i < signedFieldNames.length - 1) {
                    dataToSign.append(",");
                }
            }

            log.debug("Data to sign (length={}): {}", dataToSign.length(),
                    dataToSign.length() > 200 ? dataToSign.substring(0, 200) + "..." : dataToSign);

            // Create HMAC-SHA256 signature
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256_HMAC.init(secret_key);

            byte[] signatureBytes = sha256_HMAC.doFinal(
                    dataToSign.toString().getBytes(StandardCharsets.UTF_8)
            );
            String signature = Base64.getEncoder().encodeToString(signatureBytes);

            log.debug("Generated signature for {} fields", signedFieldNames.length);
            return signature;

        } catch (Exception e) {
            log.error("Error generating signature: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Verify signature from CyberSource callback
     * Critical security check to ensure callback is from CyberSource
     *
     * @param responseParams Parameters from CyberSource callback
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(Map<String, String> responseParams) {
        try {
            String receivedSignature = responseParams.get("signature");
            if (receivedSignature == null || receivedSignature.isEmpty()) {
                log.warn("No signature in CyberSource response");
                return false;
            }

            // Create params copy without signature for verification
            Map<String, String> paramsForVerification = new LinkedHashMap<>(responseParams);
            paramsForVerification.remove("signature");

            // Generate expected signature
            String expectedSignature = generateSignature(paramsForVerification);

            boolean isValid = expectedSignature.equals(receivedSignature);

            if (!isValid) {
                log.error("Signature verification failed!");
                log.error("Expected: {}", expectedSignature);
                log.error("Received: {}", receivedSignature);
            } else {
                log.info("Signature verification successful");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Handle CyberSource callback/response
     * Processes payment completion, updates payment and transaction records
     *
     * @param callbackData Parameters from CyberSource callback
     */
    @Transactional
    public void handleCallback(Map<String, String> callbackData) {
        try {
            String transactionId = firstNonBlank(
                    callbackData.get("transaction_id"),
                    callbackData.get("req_transaction_id")
            );
            String transactionUuid = firstNonBlank(
                    callbackData.get("req_transaction_uuid"),
                    callbackData.get("transaction_uuid")
            );

            log.info("Processing CyberSource callback for transaction: {}", transactionId);

            // CRITICAL SECURITY CHECK: Verify signature first
            if (!verifySignature(callbackData)) {
                log.error("❌ SECURITY ALERT: Invalid signature in CyberSource callback for transaction {}", transactionId);
                throw new SecurityException("Invalid CyberSource callback signature");
            }

            String decision = Optional.ofNullable(callbackData.get("decision"))
                    .map(String::toUpperCase)
                    .orElse("ERROR");
            String reasonCode = callbackData.get("reason_code");
            String message = callbackData.get("message");

            // Resolve transaction record from callback payload with safe fallbacks.
            UUID callbackPaymentId = extractPaymentIdFromCallback(callbackData);
            CyberSourceTransaction transaction = resolveTransaction(transactionUuid, transactionId, callbackPaymentId);

            // Update transaction with response data
            updateTransactionWithResponse(transaction, callbackData);

            UUID paymentId = callbackPaymentId != null ? callbackPaymentId : transaction.getPaymentId();
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

            if (isTerminalForDecision(payment, decision)) {
                log.info(
                        "Ignoring duplicate CyberSource callback for payment {} with decision {} and current status {}",
                        payment.getId(), decision, payment.getStatus()
                );
                return;
            }

            // Process based on decision
            if ("ACCEPT".equalsIgnoreCase(decision)) {
                handleAcceptedPayment(payment, callbackData, transactionId);
            } else if ("DECLINE".equalsIgnoreCase(decision)) {
                handleDeclinedPayment(payment, message, reasonCode);
            } else if ("REVIEW".equalsIgnoreCase(decision)) {
                handleReviewPayment(payment, message);
            } else {
                handleErrorPayment(payment, message, reasonCode);
            }

        } catch (Exception e) {
            log.error("Error handling CyberSource callback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to handle callback", e);
        }
    }

    /**
     * Handle accepted/approved payment
     */
    private void handleAcceptedPayment(Payment payment, Map<String, String> callbackData, String transactionId) {
        String authCode = callbackData.get("auth_code");
        String cardType = firstNonBlank(callbackData.get("req_card_type"), callbackData.get("card_type"));
        String cardNumber = firstNonBlank(callbackData.get("req_card_number"), callbackData.get("card_number"));

        // Extract last 4 digits from masked card number
        String lastFour = null;
        if (cardNumber != null && cardNumber.length() >= 4) {
            lastFour = cardNumber.substring(cardNumber.length() - 4);
        }

        // Update payment as successful
        payment.markAsCardPaymentSuccessful(transactionId, authCode, cardType, lastFour);
        paymentRepository.save(payment);
        mpesaService.applyPostPaymentActions(payment, true);
        subscriptionService.registerAutoRenewCardFromCyberSourceCallback(payment, callbackData);

        log.info("✅ Payment {} marked as successful (Card: {}****)", payment.getId(), lastFour);
    }

    /**
     * Handle declined payment
     */
    private void handleDeclinedPayment(Payment payment, String message, String reasonCode) {
        payment.markAsCardPaymentFailed(message, reasonCode);
        paymentRepository.save(payment);
        mpesaService.applyPostPaymentActions(payment, false);

        log.warn("❌ Payment {} declined. Reason: {} - {}", payment.getId(), reasonCode, message);
    }

    /**
     * Handle payment requiring review
     */
    private void handleReviewPayment(Payment payment, String message) {
        payment.setErrorMessage("Payment under review: " + message);
        paymentRepository.save(payment);

        log.info("⚠️ Payment {} requires manual review", payment.getId());
    }

    /**
     * Handle payment error
     */
    private void handleErrorPayment(Payment payment, String message, String reasonCode) {
        payment.markAsCardPaymentFailed(message, reasonCode);
        paymentRepository.save(payment);
        mpesaService.applyPostPaymentActions(payment, false);

        log.error("❌ Payment {} error. Reason: {} - {}", payment.getId(), reasonCode, message);
    }

    /**
     * Build comma-separated list of fields to be signed
     * All fields in this list MUST be present in the params map
     */
    private String buildSignedFieldNames() {
        return String.join(",", Arrays.asList(
                "access_key",
                "profile_id",
                "transaction_uuid",
                "signed_field_names",
                "unsigned_field_names",
                "signed_date_time",
                "locale",
                "transaction_type",
                "reference_number",
                "amount",
                "currency",
                "payment_method",
                "override_custom_receipt_page",
                "override_custom_cancel_page",
                "merchant_defined_data1",
                "merchant_defined_data2",
                "merchant_defined_data3",
                "merchant_defined_data4"
        ));
    }

    /**
     * Save initial CyberSource transaction record
     */
    private void saveCyberSourceTransaction(Payment payment, String transactionUuid,
                                           String referenceNumber, Map<String, String> params) {
        try {
            CyberSourceTransaction transaction = new CyberSourceTransaction();
            transaction.setPaymentId(payment.getId());
            transaction.setTransactionUuid(transactionUuid);
            transaction.setReqReferenceNumber(referenceNumber);
            transaction.setAmount(payment.getAmount());
            transaction.setCurrency(payment.getCurrency());
            transaction.setDecision("PENDING");
            transaction.setRawRequest(objectMapper.writeValueAsString(params));

            cyberSourceTransactionRepository.save(transaction);

            log.info("Saved CyberSource transaction record: {}", transaction.getId());

        } catch (Exception e) {
            log.error("Error saving CyberSource transaction: {}", e.getMessage(), e);
            // Don't throw - this is non-critical
        }
    }

    /**
     * Update transaction with CyberSource response data
     */
    private void updateTransactionWithResponse(CyberSourceTransaction transaction,
                                              Map<String, String> response) {
        try {
            transaction.setTransactionId(firstNonBlank(
                    response.get("transaction_id"),
                    response.get("req_transaction_id")
            ));
            transaction.setRequestId(firstNonBlank(
                    response.get("request_id"),
                    response.get("req_reference_number")
            ));
            transaction.setReqReferenceNumber(response.get("req_reference_number"));
            transaction.setDecision(response.get("decision"));
            transaction.setReasonCode(response.get("reason_code"));
            transaction.setMessage(response.get("message"));
            transaction.setAuthCode(response.get("auth_code"));
            transaction.setAuthResponse(response.get("auth_response"));

            if (response.get("auth_amount") != null) {
                transaction.setAuthAmount(new BigDecimal(response.get("auth_amount")));
            }

            transaction.setCardType(firstNonBlank(response.get("req_card_type"), response.get("card_type")));
            transaction.setCardNumber(firstNonBlank(response.get("req_card_number"), response.get("card_number"))); // Already masked
            transaction.setSignedFieldNames(response.get("signed_field_names"));
            transaction.setSignature(response.get("signature"));
            transaction.setRawResponse(objectMapper.writeValueAsString(response));

            if ("ACCEPT".equalsIgnoreCase(response.get("decision"))) {
                transaction.setAuthTime(LocalDateTime.now());
            }

            cyberSourceTransactionRepository.save(transaction);

            log.info("Updated CyberSource transaction {} with response data", transaction.getId());

        } catch (Exception e) {
            log.error("Error updating transaction with response: {}", e.getMessage(), e);
            // Don't throw - continue processing
        }
    }

    private CyberSourceTransaction resolveTransaction(String transactionUuid, String transactionId, UUID paymentId) {
        if (transactionUuid != null && !transactionUuid.isBlank()) {
            Optional<CyberSourceTransaction> byUuid = cyberSourceTransactionRepository.findByTransactionUuid(transactionUuid);
            if (byUuid.isPresent()) {
                return byUuid.get();
            }
        }

        if (transactionId != null && !transactionId.isBlank()) {
            Optional<CyberSourceTransaction> byTransactionId = cyberSourceTransactionRepository.findByTransactionId(transactionId);
            if (byTransactionId.isPresent()) {
                return byTransactionId.get();
            }
        }

        if (paymentId != null) {
            Optional<CyberSourceTransaction> byPayment = cyberSourceTransactionRepository.findByPaymentId(paymentId);
            if (byPayment.isPresent()) {
                return byPayment.get();
            }

            // If the initial tracking write failed, rebuild the transaction record from callback data.
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for callback paymentId: " + paymentId));

            CyberSourceTransaction rebuilt = new CyberSourceTransaction();
            rebuilt.setPaymentId(paymentId);
            rebuilt.setTransactionUuid(
                    transactionUuid != null && !transactionUuid.isBlank() ? transactionUuid : UUID.randomUUID().toString()
            );
            rebuilt.setTransactionId(transactionId);
            rebuilt.setAmount(payment.getAmount());
            rebuilt.setCurrency(payment.getCurrency());
            rebuilt.setDecision("PENDING");
            return cyberSourceTransactionRepository.save(rebuilt);
        }

        throw new RuntimeException(String.format(
                "CyberSource transaction not found (uuid=%s, transactionId=%s, paymentId=%s)",
                transactionUuid,
                transactionId,
                paymentId
        ));
    }

    private UUID extractPaymentIdFromCallback(Map<String, String> callbackData) {
        String paymentIdRaw = firstNonBlank(
                callbackData.get("req_merchant_defined_data4"),
                callbackData.get("merchant_defined_data4")
        );

        if (paymentIdRaw == null || paymentIdRaw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(paymentIdRaw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to parse payment ID from merchant_defined_data4: {}", paymentIdRaw);
            return null;
        }
    }

    private boolean isTerminalForDecision(Payment payment, String decision) {
        if ("ACCEPT".equalsIgnoreCase(decision)) {
            return payment.getStatus() == Payment.PaymentStatus.COMPLETED;
        }

        if ("DECLINE".equalsIgnoreCase(decision) || "CANCEL".equalsIgnoreCase(decision) || "ERROR".equalsIgnoreCase(decision)) {
            return payment.getStatus() == Payment.PaymentStatus.FAILED || payment.getStatus() == Payment.PaymentStatus.CANCELLED;
        }

        if ("REVIEW".equalsIgnoreCase(decision)) {
            return payment.getStatus() == Payment.PaymentStatus.COMPLETED || payment.getStatus() == Payment.PaymentStatus.FAILED;
        }

        return false;
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

    /**
     * Charge a stored CyberSource card/token for subscription renewal.
     * Returns a structured result so scheduler can decide status transitions.
     */
    @Transactional
    public RecurringChargeResult chargeSubscriptionAutoRenewal(Subscription subscription) {
        return chargeSubscriptionAutoRenewal(subscription, null, "AUTO");
    }

    /**
     * Charge a stored CyberSource card/token for subscription renewal with optional invoice linkage.
     */
    @Transactional
    public RecurringChargeResult chargeSubscriptionAutoRenewal(Subscription subscription,
                                                               Invoice invoice,
                                                               String renewalMode) {
        if (subscription == null || subscription.getId() == null) {
            return RecurringChargeResult.failed("Subscription is required");
        }

        if (!recurringEnabled) {
            return RecurringChargeResult.failed("Recurring billing is disabled");
        }

        if (!subscription.hasAutoRenewPaymentMethod()) {
            return RecurringChargeResult.failed("No reusable card token found for this subscription");
        }

        LocalDateTime pendingCutoff = LocalDateTime.now().minusMinutes(20);
        if (invoice != null && invoice.getId() != null) {
            Optional<Payment> latestInvoicePayment = paymentRepository.findTopByInvoiceIdOrderByCreatedAtDesc(invoice.getId());
            if (latestInvoicePayment.isPresent()) {
                Payment latestPayment = latestInvoicePayment.get();
                if (latestPayment.getStatus() == Payment.PaymentStatus.PENDING
                        && latestPayment.getCreatedAt() != null
                        && latestPayment.getCreatedAt().isAfter(pendingCutoff)) {
                    return RecurringChargeResult.failed("Renewal payment is already in progress");
                }
            }
        }

        List<Payment> subscriptionPayments = paymentRepository.findBySubscriptionId(subscription.getId());
        for (Payment existingPayment : subscriptionPayments) {
            if (existingPayment.getStatus() == Payment.PaymentStatus.PENDING
                    && existingPayment.getCreatedAt() != null
                    && existingPayment.getCreatedAt().isAfter(pendingCutoff)) {
                return RecurringChargeResult.failed("Renewal payment is already in progress");
            }
        }

        BigDecimal amount = invoice != null && invoice.getAmount() != null
                ? invoice.getAmount().setScale(2, java.math.RoundingMode.HALF_UP)
                : resolveRenewalAmount(subscription);
        String currency = invoice != null && invoice.getCurrency() != null && !invoice.getCurrency().isBlank()
                ? invoice.getCurrency().trim().toUpperCase(Locale.ROOT)
                : resolveRenewalCurrency(subscription);
        String referenceCode = (invoice != null && invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()
                ? invoice.getInvoiceNumber().trim()
                : "SUB-RENEW-" + subscription.getId().toString().substring(0, 8).toUpperCase(Locale.ROOT))
                + "-" + System.currentTimeMillis();
        String normalizedRenewalMode = renewalMode == null || renewalMode.isBlank()
                ? "AUTO"
                : renewalMode.trim().toUpperCase(Locale.ROOT);

        Payment payment = new Payment();
        payment.setUserId(subscription.getUserId());
        payment.setPayerId(subscription.getUserId());
        payment.setRecipientId(subscription.getUserId());
        payment.setSubscriptionId(subscription.getId());
        if (invoice != null && invoice.getId() != null) {
            payment.setPaymentType(Payment.PaymentType.INVOICE);
            payment.setInvoiceId(invoice.getId());
        } else {
            payment.setPaymentType(Payment.PaymentType.SUBSCRIPTION);
        }
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setPaymentMethod(Payment.PaymentMethod.CARD);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setDescription(invoice != null && invoice.getDescription() != null && !invoice.getDescription().isBlank()
                ? invoice.getDescription()
                : String.format("Subscription renewal for %s",
                subscription.getPlan() != null ? subscription.getPlan().getName() : "plan"));
        payment.setMetadata(String.format(
                "{\"renewalMode\":\"%s\",\"subscriptionId\":\"%s\",\"invoiceId\":\"%s\",\"referenceCode\":\"%s\"}",
                normalizedRenewalMode,
                subscription.getId(),
                invoice != null ? invoice.getId() : null,
                referenceCode
        ));
        payment = paymentRepository.save(payment);

        try {
            Map<String, Object> requestPayload = buildRecurringPaymentRequest(
                    subscription,
                    amount,
                    currency,
                    referenceCode
            );
            String requestBody = objectMapper.writeValueAsString(requestPayload);

            String endpoint = restApiBaseUrl.replaceAll("/+$", "") + "/pts/v2/payments";
            URI endpointUri = URI.create(endpoint);
            HttpHeaders headers = buildSignedRestHeaders("post", "/pts/v2/payments", requestBody, endpointUri);
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);
            JsonResponse parsed = parseCyberSourceResponse(response.getBody());

            if (response.getStatusCode().is2xxSuccessful() && isRecurringSuccessStatus(parsed.status)) {
                payment.markAsCardPaymentSuccessful(
                        parsed.transactionId,
                        parsed.authCode,
                        subscription.getAutoRenewCardType(),
                        subscription.getAutoRenewCardLastFour()
                );
                payment.setGatewayReference(referenceCode);
                paymentRepository.save(payment);
                if (invoice != null) {
                    mpesaService.applyPostPaymentActions(payment, true);
                }

                return RecurringChargeResult.success(
                        payment.getId(),
                        parsed.transactionId,
                        parsed.status,
                        parsed.message
                );
            }

            String failureMessage = firstNonBlank(
                    parsed.message,
                    "Recurring charge was not approved"
            );
            payment.markAsCardPaymentFailed(failureMessage, parsed.reasonCode);
            paymentRepository.save(payment);
            if (invoice != null) {
                mpesaService.applyPostPaymentActions(payment, false);
            }
            return RecurringChargeResult.failed(failureMessage, payment.getId());

        } catch (HttpStatusCodeException e) {
            JsonResponse parsed = parseCyberSourceResponse(e.getResponseBodyAsString());
            String failureMessage = firstNonBlank(
                    parsed.message,
                    "Recurring charge failed with HTTP " + e.getStatusCode().value()
            );
            payment.markAsCardPaymentFailed(failureMessage, parsed.reasonCode);
            paymentRepository.save(payment);
            if (invoice != null) {
                mpesaService.applyPostPaymentActions(payment, false);
            }
            return RecurringChargeResult.failed(failureMessage, payment.getId());
        } catch (Exception e) {
            String failureMessage = "Recurring charge failed: " + e.getMessage();
            payment.markAsCardPaymentFailed(failureMessage, null);
            paymentRepository.save(payment);
            if (invoice != null) {
                mpesaService.applyPostPaymentActions(payment, false);
            }
            log.error("Recurring charge failed for subscription {}: {}", subscription.getId(), e.getMessage(), e);
            return RecurringChargeResult.failed(failureMessage, payment.getId());
        }
    }

    private Map<String, Object> buildRecurringPaymentRequest(Subscription subscription,
                                                             BigDecimal amount,
                                                             String currency,
                                                             String referenceCode) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("clientReferenceInformation", Map.of("code", referenceCode));
        root.put("processingInformation", Map.of("commerceIndicator", "recurring"));
        root.put("orderInformation", Map.of(
                "amountDetails", Map.of(
                        "totalAmount", amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        "currency", currency
                )
        ));
        root.put("paymentInformation", Map.of(
                "customer", Map.of("id", subscription.getAutoRenewCustomerToken()),
                "paymentInstrument", Map.of("id", subscription.getAutoRenewPaymentInstrumentId())
        ));
        return root;
    }

    private HttpHeaders buildSignedRestHeaders(String httpMethod,
                                               String resourcePath,
                                               String requestBody,
                                               URI endpointUri) {
        String host = endpointUri.getHost();
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        String digest = buildDigestHeader(requestBody);
        String requestTarget = httpMethod.toLowerCase(Locale.ROOT) + " " + resourcePath;

        String signaturePayload = String.join("\n",
                "host: " + host,
                "date: " + date,
                "(request-target): " + requestTarget,
                "digest: " + digest,
                "v-c-merchant-id: " + merchantId
        );
        String signature = hmacSha256Base64(signaturePayload, secretKey);

        String signatureHeader = String.format(
                "keyid=\"%s\", algorithm=\"HmacSHA256\", headers=\"host date (request-target) digest v-c-merchant-id\", signature=\"%s\"",
                accessKey,
                signature
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Host", host);
        headers.set("Date", date);
        headers.set("Digest", digest);
        headers.set("v-c-merchant-id", merchantId);
        headers.set("Signature", signatureHeader);
        return headers;
    }

    private String buildDigestHeader(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return "SHA-256=" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Digest header", e);
        }
    }

    private String hmacSha256Base64(String payload, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign CyberSource REST request", e);
        }
    }

    private JsonResponse parseCyberSourceResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new JsonResponse(null, null, null, null, null);
        }

        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(responseBody);
            String id = node.path("id").asText(null);
            String status = node.path("status").asText(null);
            String reason = firstNonBlank(
                    node.path("errorInformation").path("reason").asText(null),
                    node.path("reason").asText(null),
                    node.path("reasonCode").asText(null)
            );
            String message = firstNonBlank(
                    node.path("errorInformation").path("message").asText(null),
                    node.path("message").asText(null)
            );
            String authCode = firstNonBlank(
                    node.path("processorInformation").path("approvalCode").asText(null),
                    node.path("authCode").asText(null)
            );
            return new JsonResponse(id, status, reason, message, authCode);
        } catch (Exception e) {
            return new JsonResponse(null, null, null, responseBody, null);
        }
    }

    private boolean isRecurringSuccessStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return RECURRING_SUCCESS_STATUSES.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private BigDecimal resolveRenewalAmount(Subscription subscription) {
        if (subscription.getPlan() == null) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return subscription.getPlan()
                .resolvePriceForInterval(subscription.getBillingInterval())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String resolveRenewalCurrency(Subscription subscription) {
        if (subscription.getPlan() == null || subscription.getPlan().getCurrency() == null
                || subscription.getPlan().getCurrency().isBlank()) {
            return "KES";
        }
        return subscription.getPlan().getCurrency().trim().toUpperCase(Locale.ROOT);
    }

    public static class RecurringChargeResult {
        private final boolean success;
        private final UUID paymentId;
        private final String transactionId;
        private final String status;
        private final String message;

        private RecurringChargeResult(boolean success, UUID paymentId, String transactionId, String status, String message) {
            this.success = success;
            this.paymentId = paymentId;
            this.transactionId = transactionId;
            this.status = status;
            this.message = message;
        }

        public static RecurringChargeResult success(UUID paymentId, String transactionId, String status, String message) {
            return new RecurringChargeResult(true, paymentId, transactionId, status, message);
        }

        public static RecurringChargeResult failed(String message) {
            return new RecurringChargeResult(false, null, null, null, message);
        }

        public static RecurringChargeResult failed(String message, UUID paymentId) {
            return new RecurringChargeResult(false, paymentId, null, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public UUID getPaymentId() {
            return paymentId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class JsonResponse {
        private final String transactionId;
        private final String status;
        private final String reasonCode;
        private final String message;
        private final String authCode;

        private JsonResponse(String transactionId, String status, String reasonCode, String message, String authCode) {
            this.transactionId = transactionId;
            this.status = status;
            this.reasonCode = reasonCode;
            this.message = message;
            this.authCode = authCode;
        }
    }

    /**
     * Get CyberSource payment endpoint URL
     */
    public String getEndpointUrl() {
        return endpointUrl;
    }

    /**
     * Get transaction by payment ID
     */
    public CyberSourceTransaction getTransactionByPaymentId(UUID paymentId) {
        return cyberSourceTransactionRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new RuntimeException("Transaction not found for payment: " + paymentId));
    }

    /**
     * Get transaction by transaction UUID
     */
    public CyberSourceTransaction getTransactionByUuid(String transactionUuid) {
        return cyberSourceTransactionRepository.findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new RuntimeException("Transaction not found for UUID: " + transactionUuid));
    }
}
