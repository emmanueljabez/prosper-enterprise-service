package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.dto.MpesaCallbackDTO;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.service.MpesaService;
import com.prosper.prospermentor.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    public PaymentController(MpesaService mpesaService, PaymentRepository paymentRepository) {
        this.mpesaService = mpesaService;
        this.paymentRepository = paymentRepository;
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
     * Get user's payment history
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user payment history", description = "Get payment history for a user")
    public ResponseEntity<Map<String, Object>> getUserPayments(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Getting payment history for user: {}", userId);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payments", payments.getContent());
            response.put("currentPage", payments.getNumber());
            response.put("totalPages", payments.getTotalPages());
            response.put("totalItems", payments.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting user payments: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to get payment history: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
}