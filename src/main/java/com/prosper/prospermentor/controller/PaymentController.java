package com.prosper.prospermentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.dto.GenericPaymentRequest;
import com.prosper.prospermentor.dto.MpesaCallbackDTO;
import com.prosper.prospermentor.dto.CyberSourcePaymentRequest;
import com.prosper.prospermentor.dto.CyberSourcePaymentResponse;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.CyberSourceTransaction;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.specification.PaymentSpecification;
import com.prosper.prospermentor.service.CurrencyService;
import com.prosper.prospermentor.service.MpesaService;
import com.prosper.prospermentor.service.CyberSourceService;
import com.prosper.prospermentor.service.ProfileService;
import com.prosper.prospermentor.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

/**
 * REST Controller for handling payments (primarily Mpesa)
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment and Mpesa integration APIs")
@Slf4j
public class PaymentController {

    private final MpesaService mpesaService;
    private final PaymentRepository paymentRepository;
    private final CurrencyService currencyService;
    private final CyberSourceService cyberSourceService;
    private final ProfileService profileService;
    private final ObjectMapper objectMapper;

    public PaymentController(MpesaService mpesaService,
                           PaymentRepository paymentRepository,
                           CurrencyService currencyService,
                           CyberSourceService cyberSourceService,
                           ProfileService profileService,
                           ObjectMapper objectMapper) {
        this.mpesaService = mpesaService;
        this.paymentRepository = paymentRepository;
        this.currencyService = currencyService;
        this.cyberSourceService = cyberSourceService;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiate Mpesa STK Push payment for a session
     */
    @PostMapping("/mpesa/stk-push/session")
    @Operation(summary = "Initiate session payment", description = "Initiate Mpesa STK Push payment for a session booking")
    public ResponseEntity<Map<String, Object>> initiateSessionPayment(
            @RequestParam UUID userId,
            @RequestParam UUID sessionId,
            @RequestParam BigDecimal amount,
            @RequestParam String phoneNumber,
            @RequestParam(required = false) String description) {

        log.info("Initiating session payment for user {} session {} amount {}", userId, sessionId, amount);

        try {
            String paymentDescription = description != null ? description : "Session payment";

            Payment payment = mpesaService.initiateSTKPush(
                    userId,
                    sessionId,
                    null,
                    Payment.PaymentType.SESSION_BOOKING,
                    amount,
                    phoneNumber,
                    paymentDescription
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "STK Push initiated successfully. Please check your phone.");
            response.put("paymentId", payment.getId());
            response.put("checkoutRequestId", payment.getCheckoutRequestId());
            response.put("amount", payment.getAmount());
            response.put("phoneNumber", payment.getPhoneNumber());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error initiating session payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to initiate payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Initiate Mpesa STK Push payment for a subscription
     */
    @PostMapping("/mpesa/stk-push/subscription")
    @Operation(summary = "Initiate subscription payment", description = "Initiate Mpesa STK Push payment for subscription")
    public ResponseEntity<Map<String, Object>> initiateSubscriptionPayment(
            @RequestParam UUID userId,
            @RequestParam UUID subscriptionId,
            @RequestParam BigDecimal amount,
            @RequestParam String phoneNumber,
            @RequestParam(required = false) String description) {

        log.info("Initiating subscription payment for user {} subscription {} amount {}",
                userId, subscriptionId, amount);

        try {
            String paymentDescription = description != null ? description : "Subscription payment";

            Payment payment = mpesaService.initiateSTKPush(
                    userId,
                    null,
                    subscriptionId,
                    Payment.PaymentType.SUBSCRIPTION,
                    amount,
                    phoneNumber,
                    paymentDescription
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "STK Push initiated successfully. Please check your phone.");
            response.put("paymentId", payment.getId());
            response.put("checkoutRequestId", payment.getCheckoutRequestId());
            response.put("amount", payment.getAmount());
            response.put("phoneNumber", payment.getPhoneNumber());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error initiating subscription payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to initiate payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Initiate Mpesa STK Push payment for generic purposes (events, top-ups, add-ons, etc.)
     * Supports currency conversion - amount will be converted from specified currency to KES
     */
    @PostMapping("/mpesa/stk-push/generic")
    @Operation(summary = "Initiate generic payment", description = "Initiate Mpesa STK Push payment for events, top-ups, add-ons, or other miscellaneous items. Supports currency conversion to KES.")
    public ResponseEntity<Map<String, Object>> initiateGenericPayment(
            @Valid @RequestBody GenericPaymentRequest request) {

        String sourceCurrency = request.getCurrency() != null && !request.getCurrency().isEmpty()
                ? request.getCurrency().toUpperCase()
                : "USD";

        log.info("Initiating generic payment for user {} type {} amount {} {}",
                request.getUserId(), request.getPaymentType(), request.getAmount(), sourceCurrency);

        try {
            // Validate and convert payment type
            Payment.PaymentType type;
            try {
                type = Payment.PaymentType.valueOf(request.getPaymentType().toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid payment type. Valid types: ADDON, TOP_UP, REFUND");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Prevent using this endpoint for session or subscription payments
            if (type == Payment.PaymentType.SESSION_BOOKING ||
                type == Payment.PaymentType.SUBSCRIPTION ||
                type == Payment.PaymentType.UPGRADE) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Use dedicated endpoints for session or subscription payments");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Validate currency is supported
            if (!currencyService.isCurrencySupported(sourceCurrency)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Unsupported currency: " + sourceCurrency + ". Please contact support.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Convert amount to KES (Mpesa only supports KES)
            BigDecimal originalAmount = request.getAmount();
            BigDecimal amountInKES = currencyService.convert(originalAmount, sourceCurrency, "KES");

            log.info("Converted {} {} to {} KES for payment", originalAmount, sourceCurrency, amountInKES);

            Payment payment = mpesaService.initiateSTKPush(
                    request.getUserId(),
                    null,
                    null,
                    type,
                    amountInKES,
                    request.getPhoneNumber(),
                    request.getDescription()
            );

            // Build metadata to include original currency and amount
            String metadataJson = String.format(
                    "{\"originalAmount\":\"%s\",\"originalCurrency\":\"%s\",\"convertedAmount\":\"%s\",\"convertedCurrency\":\"KES\"%s}",
                    originalAmount,
                    sourceCurrency,
                    amountInKES,
                    request.getMetadata() != null && !request.getMetadata().isEmpty()
                            ? ",\"additionalData\":" + request.getMetadata()
                            : ""
            );

            payment.setMetadata(metadataJson);
            mpesaService.updatePaymentMetadata(payment.getId(), metadataJson);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "STK Push initiated successfully. Please check your phone.");
            response.put("paymentId", payment.getId());
            response.put("checkoutRequestId", payment.getCheckoutRequestId());
            response.put("originalAmount", originalAmount);
            response.put("originalCurrency", sourceCurrency);
            response.put("convertedAmount", amountInKES);
            response.put("convertedCurrency", "KES");
            response.put("phoneNumber", payment.getPhoneNumber());
            response.put("paymentType", payment.getPaymentType());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Currency conversion error: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Currency conversion failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error initiating generic payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to initiate payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Query payment status
     */
    @GetMapping("/status/{checkoutRequestId}")
    @Operation(summary = "Query payment status", description = "Query the status of a payment transaction")
    public ResponseEntity<Map<String, Object>> queryPaymentStatus(@PathVariable String checkoutRequestId) {
        log.info("Querying payment status for CheckoutRequestID: {}", checkoutRequestId);

        try {
            Payment payment = mpesaService.queryTransactionStatus(checkoutRequestId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payment", payment);
            response.put("status", payment.getStatus());
            response.put("isCompleted", payment.isCompleted());

            if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                response.put("mpesaReceiptNumber", payment.getMpesaReceiptNumber());
                response.put("transactionDate", payment.getTransactionDate());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error querying payment status: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to query payment status: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get payment by ID
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID", description = "Get details of a specific payment")
    public ResponseEntity<Map<String, Object>> getPaymentById(@PathVariable UUID paymentId) {
        log.info("Getting payment: {}", paymentId);

        try {
            Payment payment = mpesaService.getPaymentById(paymentId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payment", payment);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * Get paginated payments with optional filters.
     * Admin users can query all payments. Non-admin users are restricted to their own payments.
     */
    @GetMapping
    @Operation(summary = "Get payments", description = "Get paginated payments with optional filters. " +
            "Admins can query all payments. Non-admin users only see their own payments.")
    public ResponseEntity<Map<String, Object>> getPayments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(required = false) Payment.PaymentMethod paymentMethod,
            @RequestParam(required = false) Payment.PaymentType paymentType,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) UUID subscriptionId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        try {
            UUID authenticatedUserId = getAuthenticatedUserId(authentication);
            boolean adminRequest = isAdmin(authentication);
            UUID authorizedCompanyId = getAuthorizedCompanyId(authentication);

            if (!adminRequest && authenticatedUserId == null && authorizedCompanyId == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse("Not authorized to access payments"));
            }

            if (!adminRequest && companyId != null && (authorizedCompanyId == null || !authorizedCompanyId.equals(companyId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse("Not authorized to filter payments by company"));
            }

            UUID effectiveUserId = userId;
            if (!adminRequest && companyId == null) {
                if (userId != null && !userId.equals(authenticatedUserId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse("Not authorized to access payments for this user"));
                }
                effectiveUserId = authenticatedUserId;
            } else if (!adminRequest && companyId != null && authorizedCompanyId != null && !authorizedCompanyId.equals(companyId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse("Not authorized to access payments for this company"));
            }

            if (page < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("page must be greater than or equal to 0"));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("size must be between 1 and 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Payment> payments = paymentRepository.findAll(
                    PaymentSpecification.filter(
                            effectiveUserId,
                            status,
                            paymentMethod,
                            paymentType,
                            companyId,
                            invoiceId,
                            sessionId,
                            subscriptionId,
                            search
                    ),
                    pageable
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("payments", payments.getContent());
            data.put("currentPage", payments.getNumber());
            data.put("pageSize", payments.getSize());
            data.put("totalPages", payments.getTotalPages());
            data.put("totalItems", payments.getTotalElements());
            data.put("hasNext", payments.hasNext());
            data.put("hasPrevious", payments.hasPrevious());

            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("userId", effectiveUserId);
            filters.put("status", status);
            filters.put("paymentMethod", paymentMethod);
            filters.put("paymentType", paymentType);
            filters.put("companyId", companyId);
            filters.put("invoiceId", invoiceId);
            filters.put("sessionId", sessionId);
            filters.put("subscriptionId", subscriptionId);
            filters.put("search", search);
            data.put("filters", filters);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Payments retrieved successfully");
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting payments: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Failed to get payments: " + e.getMessage()));
        }
    }

    /**
     * Get user's payment history
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user payment history", description = "Get payment history for a user")
    public ResponseEntity<Map<String, Object>> getUserPayments(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        log.info("Getting payment history for user: {}", userId);

        try {
            if (!canAccessUser(authentication, userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse("Not authorized to access payments for this user"));
            }

            if (page < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("page must be greater than or equal to 0"));
            }

            if (size < 1 || size > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("size must be between 1 and 100"));
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("payments", payments.getContent());
            response.put("currentPage", payments.getNumber());
            response.put("pageSize", payments.getSize());
            response.put("totalPages", payments.getTotalPages());
            response.put("totalItems", payments.getTotalElements());
            response.put("hasNext", payments.hasNext());
            response.put("hasPrevious", payments.hasPrevious());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting user payments: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Failed to get payment history: " + e.getMessage()));
        }
    }

    /**
     * Mpesa callback endpoint
     * This endpoint receives callbacks from Safaricom after payment processing
     */
    @PostMapping("/confirmc2b")
    @Operation(summary = "Mpesa callback", description = "Webhook endpoint for Mpesa payment callbacks")
    public ResponseEntity<Map<String, Object>> confirmc2b(@RequestBody MpesaCallbackDTO callbackData) {
        log.info("Received Mpesa callback - CheckoutRequestID: {}, ResultCode: {}",
                callbackData.getCheckoutRequestId(), callbackData.getResultCode());

        try {
            mpesaService.handleCallback(callbackData);

            Map<String, Object> response = new HashMap<>();
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing Mpesa callback: {}", e.getMessage(), e);

            // Still return success to Safaricom to avoid retries
            Map<String, Object> response = new HashMap<>();
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Success");

            return ResponseEntity.ok(response);
        }
    }

    /**
     * Cancel a payment
     */
    @PutMapping("/{paymentId}/cancel")
    @Operation(summary = "Cancel payment", description = "Cancel a pending payment")
    public ResponseEntity<Map<String, Object>> cancelPayment(@PathVariable UUID paymentId) {
        log.info("Cancelling payment: {}", paymentId);

        try {
            Payment payment = mpesaService.getPaymentById(paymentId);

            if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Cannot cancel completed payment");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            payment.cancel();
            paymentRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment cancelled successfully");
            response.put("payment", payment);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelling payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to cancel payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get payments by session
     */
    @GetMapping("/session/{sessionId}")
    @Operation(summary = "Get session payments", description = "Get all payments for a specific session")
    public ResponseEntity<Map<String, Object>> getSessionPayments(@PathVariable UUID sessionId) {
        log.info("Getting payments for session: {}", sessionId);

        try {
            var payments = paymentRepository.findBySessionId(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payments", payments);
            response.put("count", payments.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting session payments: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get session payments: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ==================== CyberSource Endpoints ====================

    /**
     * Initiate CyberSource card payment
     * Creates a payment record and generates signed CyberSource parameters
     */
    @PostMapping("/cybersource/initiate")
    @Operation(summary = "Initiate CyberSource payment",
               description = "Create payment and generate CyberSource Secure Acceptance parameters")
    public ResponseEntity<Map<String, Object>> initiateCyberSourcePayment(
            @Valid @RequestBody CyberSourcePaymentRequest request) {

        log.info("Initiating CyberSource payment for user {} amount {} {}",
                request.getUserId(), request.getAmount(), request.getCurrency());

        try {
            // Parse and validate userId
            UUID userId;
            try {
                userId = UUID.fromString(request.getUserId());
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid user ID format: " + request.getUserId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Validate and convert payment type
            Payment.PaymentType paymentType;
            try {
                paymentType = Payment.PaymentType.valueOf(request.getPaymentType().toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid payment type: " + request.getPaymentType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (paymentType == Payment.PaymentType.REFUND) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Refund payment type is not supported for CyberSource initiation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (paymentType == Payment.PaymentType.UPGRADE &&
                    (request.getSubscriptionId() == null || request.getPlanId() == null)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Upgrade card payments require both subscriptionId and planId");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (paymentType == Payment.PaymentType.ADDON && request.getSubscriptionId() == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Add-on card payments require subscriptionId");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // Create payment record
            Payment payment = new Payment();
            payment.setUserId(userId);
            payment.setPayerId(userId);
            payment.setRecipientId(userId); // Will be updated based on type
            payment.setPaymentType(paymentType);
            payment.setAmount(request.getAmount());
            payment.setCurrency(request.getCurrency().toUpperCase(Locale.ROOT));
            payment.setPaymentMethod(Payment.PaymentMethod.CARD);
            payment.setStatus(Payment.PaymentStatus.PENDING);
            payment.setDescription(request.getDescription() != null ?
                    request.getDescription() : "Card payment via CyberSource");

            // Set session/subscription relationship based on request
            if (request.getSessionId() != null) {
                payment.setSessionId(request.getSessionId());
            }

            if (request.getSubscriptionId() != null) {
                payment.setSubscriptionId(request.getSubscriptionId());
            }

            String metadata = buildCyberSourceMetadata(request, paymentType);
            if (metadata != null) {
                payment.setMetadata(metadata);
            }

            // Save payment
            Payment savedPayment = paymentRepository.save(payment);

            // Generate CyberSource parameters
            Map<String, String> cybersourceParams = cyberSourceService.generatePaymentParameters(
                    savedPayment,
                    request.getReturnUrl(),
                    request.getCancelUrl()
            );
            String endpoint = cyberSourceService.getEndpointUrl();

            // Build response
            CyberSourcePaymentResponse paymentResponse = CyberSourcePaymentResponse.builder()
                    .success(true)
                    .message("CyberSource payment initialized successfully")
                    .paymentId(savedPayment.getId())
                    .transactionId(cybersourceParams.get("transaction_uuid"))
                    .cybersourceEndpoint(endpoint)
                    .cybersourceParams(cybersourceParams)
                    .referenceNumber(cybersourceParams.get("reference_number"))
                    .build();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment initialized successfully");
            response.put("data", paymentResponse);

            log.info("CyberSource payment initialized: Payment ID {}, Transaction UUID {}",
                    savedPayment.getId(), cybersourceParams.get("transaction_uuid"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error initiating CyberSource payment: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to initiate payment: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handle CyberSource callback/return
     * Processes payment completion from CyberSource
     */
    @PostMapping("/cybersource/callback")
    @Operation(summary = "CyberSource callback",
               description = "Handle payment callback from CyberSource")
    public ResponseEntity<Map<String, Object>> handleCyberSourceCallback(
            @RequestParam Map<String, String> params) {

        log.info("Received CyberSource callback - Transaction: {}, Decision: {}",
                params.get("transaction_id"), params.get("decision"));

        try {
            // Process callback
            cyberSourceService.handleCallback(params);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Callback processed successfully");

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            log.error("Security error in CyberSource callback: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Invalid signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);

        } catch (Exception e) {
            log.error("Error processing CyberSource callback: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Callback processing failed");
            // Still return 200 to prevent CyberSource retries
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Get CyberSource transaction status
     * Query transaction details by transaction UUID or payment ID
     */
    @GetMapping("/cybersource/status/{identifier}")
    @Operation(summary = "Get CyberSource transaction status",
               description = "Get transaction status by transaction UUID or payment ID")
    public ResponseEntity<Map<String, Object>> getCyberSourceTransactionStatus(
            @PathVariable String identifier) {

        log.info("Querying CyberSource transaction status: {}", identifier);

        try {
            CyberSourceTransaction transaction;
            try {
                transaction = cyberSourceService.getTransactionByUuid(identifier);
            } catch (Exception ignored) {
                UUID paymentId = UUID.fromString(identifier);
                transaction = cyberSourceService.getTransactionByPaymentId(paymentId);
            }

            // Get associated payment
            Payment payment = paymentRepository.findById(transaction.getPaymentId())
                    .orElse(null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("transaction", transaction);
            response.put("payment", payment);
            response.put("decision", transaction.getDecision());
            response.put("status", transaction.getFriendlyStatus());
            response.put("isApproved", transaction.isApproved());
            response.put("isDeclined", transaction.isDeclined());
            response.put("requiresReview", transaction.requiresReview());

            if (transaction.isApproved()) {
                response.put("authCode", transaction.getAuthCode());
                response.put("cardType", transaction.getCardType());
                response.put("cardLastFour", transaction.getCardLastFour());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error querying transaction status: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Transaction not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * Get all CyberSource transactions for a payment
     */
    @GetMapping("/cybersource/payment/{paymentId}")
    @Operation(summary = "Get CyberSource transaction by payment",
               description = "Get transaction details for a specific payment")
    public ResponseEntity<Map<String, Object>> getCyberSourceByPayment(
            @PathVariable UUID paymentId) {

        log.info("Getting CyberSource transaction for payment: {}", paymentId);

        try {
            CyberSourceTransaction transaction = cyberSourceService.getTransactionByPaymentId(paymentId);
            Payment payment = paymentRepository.findById(paymentId).orElse(null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("transaction", transaction);
            response.put("payment", payment);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting transaction: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Transaction not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    private String buildCyberSourceMetadata(CyberSourcePaymentRequest request, Payment.PaymentType paymentType) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();

            if (paymentType == Payment.PaymentType.UPGRADE && request.getPlanId() != null) {
                metadata.put("targetPlanId", request.getPlanId().toString());
            } else if (request.getPlanId() != null) {
                metadata.put("planId", request.getPlanId().toString());
            }

            if (request.getAddonQuantity() != null) {
                metadata.put("addonQuantity", request.getAddonQuantity());
            }

            if (request.getItemId() != null) {
                metadata.put("itemId", request.getItemId().toString());
            }

            if (metadata.isEmpty()) {
                return null;
            }

            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize CyberSource payment metadata: {}", e.getMessage());
            return null;
        }
    }

    private boolean canAccessUser(Authentication authentication, UUID requestedUserId) {
        if (requestedUserId == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        UUID authenticatedUserId = getAuthenticatedUserId(authentication);
        return authenticatedUserId != null && authenticatedUserId.equals(requestedUserId);
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUserDetails userDetails) {
            return userDetails.isAdmin();
        }

        return false;
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUserDetails userDetails) {
            return userDetails.getUserIdAsUuid();
        }

        return null;
    }

    private UUID getAuthorizedCompanyId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof SupabaseUserDetails userDetails) || !userDetails.isCompanyAdmin()) {
            return null;
        }

        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            return null;
        }

        return profileService.getProfileWithCompany(userId)
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}
