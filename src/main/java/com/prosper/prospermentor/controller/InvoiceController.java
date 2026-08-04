package com.prosper.prospermentor.controller;

import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.BillingInterval;
import com.prosper.prospermentor.security.SupabaseUserDetails;
import com.prosper.prospermentor.service.InvoiceService;
import com.prosper.prospermentor.service.ProfileService;
import com.prosper.prospermentor.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Unified invoice-driven payment APIs")
@Slf4j
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final SubscriptionService subscriptionService;
    private final ProfileService profileService;

    public InvoiceController(InvoiceService invoiceService,
                             SubscriptionService subscriptionService,
                             ProfileService profileService) {
        this.invoiceService = invoiceService;
        this.subscriptionService = subscriptionService;
        this.profileService = profileService;
    }

    @PostMapping
    @Operation(summary = "Create invoice", description = "Create a payable invoice that can be settled via M-Pesa or card.")
    public ResponseEntity<Map<String, Object>> createInvoice(@RequestBody CreateInvoiceRequest request) {
        try {
            if (request == null || request.getPayerUserId() == null || request.getPayerUserId().isBlank()) {
                return badRequest("payerUserId is required");
            }
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return badRequest("amount must be greater than 0");
            }

            UUID payerUserId;
            try {
                payerUserId = UUID.fromString(request.getPayerUserId().trim());
            } catch (IllegalArgumentException ex) {
                return badRequest("payerUserId must be a valid UUID");
            }

            Invoice invoice = invoiceService.createInvoice(
                    payerUserId,
                    request.getAmount(),
                    request.getCurrency(),
                    request.getDescription(),
                    request.getMetadata(),
                    request.getRedirectSuccessUrl(),
                    request.getRedirectCancelUrl(),
                    request.getExpiresAt()
            );

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("invoiceId", invoice.getId());
            data.put("invoiceNumber", invoice.getInvoiceNumber());
            data.put("publicToken", invoice.getPublicToken());
            data.put("status", invoice.getStatus());
            data.put("amount", invoice.getAmount());
            data.put("currency", invoice.getCurrency());
            data.put("paymentUrl", invoiceService.buildPaymentUrl(invoice));
            data.put("expiresAt", invoice.getExpiresAt());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Invoice created successfully");
            response.put("data", data);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to create invoice: {}", e.getMessage(), e);
            return serverError("Failed to create invoice");
        }
    }

    @GetMapping("/public/{publicToken}")
    @Operation(summary = "Get public invoice", description = "Get invoice details for payment page by public token.")
    public ResponseEntity<Map<String, Object>> getPublicInvoice(@PathVariable String publicToken) {
        try {
            Map<String, Object> data = invoiceService.buildPublicInvoicePayload(publicToken);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return notFound("Invoice not found");
        } catch (Exception e) {
            log.error("Failed to fetch public invoice: {}", e.getMessage(), e);
            return serverError("Failed to fetch invoice");
        }
    }

    @GetMapping("/public/{publicToken}/status")
    @Operation(summary = "Get invoice status", description = "Get latest invoice and payment status for polling.")
    public ResponseEntity<Map<String, Object>> getPublicInvoiceStatus(@PathVariable String publicToken) {
        return getPublicInvoice(publicToken);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user invoices", description = "Get invoices raised for a specific payer user.")
    public ResponseEntity<Map<String, Object>> getUserInvoices(@PathVariable String userId,
                                                               Authentication authentication) {
        try {
            UUID payerUserId = parseUuid(userId, "userId");
            if (!isAuthorizedUserRequest(authentication, payerUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("Not authorized to access invoices for this user"));
            }

            List<Map<String, Object>> invoices = invoiceService.buildUserInvoiceListPayload(payerUserId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", invoices);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to fetch user invoices: {}", e.getMessage(), e);
            return serverError("Failed to fetch user invoices");
        }
    }

    @GetMapping("/company/{companyId}")
    @Operation(summary = "Get company invoices", description = "Get invoices raised for a specific company.")
    public ResponseEntity<Map<String, Object>> getCompanyInvoices(@PathVariable String companyId,
                                                                  Authentication authentication) {
        try {
            UUID companyUuid = parseUuid(companyId, "companyId");
            authorizeCompanyRequest(authentication, companyUuid);

            List<Map<String, Object>> invoices = invoiceService.buildCompanyInvoiceListPayload(companyUuid);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", invoices);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch company invoices: {}", e.getMessage(), e);
            return serverError("Failed to fetch company invoices");
        }
    }

    private boolean isAuthorizedUserRequest(Authentication authentication, UUID requestedUserId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SupabaseUserDetails userDetails) {
            if (userDetails.isAdmin()) {
                return true;
            }
            UUID authUserId = userDetails.getUserIdAsUuid();
            return authUserId != null && authUserId.equals(requestedUserId);
        }

        return false;
    }

    private void authorizeCompanyRequest(Authentication authentication, UUID companyId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SupabaseUserDetails userDetails)) {
            throw new SecurityException("Authentication required");
        }

        if (userDetails.isAdmin()) {
            return;
        }

        if (!userDetails.isCompanyAdmin()) {
            throw new SecurityException("Company admin access is required");
        }

        UUID userId = userDetails.getUserIdAsUuid();
        if (userId == null) {
            throw new SecurityException("Invalid authenticated user");
        }

        UUID profileCompanyId = profileService.getProfileWithCompany(userId)
                .map(profile -> profile.getCompany() != null ? profile.getCompany().getId() : null)
                .orElse(null);

        if (profileCompanyId == null || !profileCompanyId.equals(companyId)) {
            throw new SecurityException("Not authorized to access invoices for this company");
        }
    }

    @PostMapping("/public/{publicToken}/pay")
    @Operation(summary = "Initiate invoice payment", description = "Initiate payment for invoice using M-Pesa or card.")
    public ResponseEntity<Map<String, Object>> payInvoice(@PathVariable String publicToken,
                                                          @RequestBody InvoicePaymentRequest request) {
        try {
            if (request == null || request.getMethod() == null || request.getMethod().isBlank()) {
                return badRequest("method is required");
            }

            Map<String, Object> paymentData = invoiceService.initiatePayment(
                    publicToken,
                    request.getMethod(),
                    request.getPhoneNumber(),
                    request.getReturnUrl(),
                    request.getCancelUrl()
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Payment initiated");
            response.put("data", paymentData);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
        } catch (RuntimeException e) {
            if ("Invoice not found".equalsIgnoreCase(e.getMessage())) {
                return notFound("Invoice not found");
            }
            log.error("Failed to initiate invoice payment: {}", e.getMessage(), e);
            return serverError("Failed to initiate payment");
        }
    }

    @PostMapping("/quotes/plan")
    @Operation(summary = "Quote plan invoice", description = "Calculate server-side amount and metadata for subscription plan purchase/upgrade invoice.")
    public ResponseEntity<Map<String, Object>> quotePlanInvoice(@RequestBody PlanInvoiceQuoteRequest request) {
        try {
            if (request == null) {
                return badRequest("request body is required");
            }

            UUID userId = parseUuid(request.getUserId(), "userId");
            UUID planId = parseUuid(request.getPlanId(), "planId");
            Map<String, Object> quoteData = subscriptionService.quotePlanInvoice(
                    userId,
                    planId,
                    BillingInterval.fromString(request.getBillingInterval())
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Plan quote generated");
            response.put("data", quoteData);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to quote plan invoice: {}", e.getMessage(), e);
            return serverError("Failed to calculate plan quote");
        }
    }

    @PostMapping("/quotes/addon")
    @Operation(summary = "Quote add-on invoice", description = "Calculate server-side amount and metadata for session add-on invoice.")
    public ResponseEntity<Map<String, Object>> quoteAddonInvoice(@RequestBody AddonInvoiceQuoteRequest request) {
        try {
            if (request == null) {
                return badRequest("request body is required");
            }

            UUID userId = parseUuid(request.getUserId(), "userId");
            if (request.getQuantity() == null) {
                return badRequest("quantity is required");
            }

            Map<String, Object> quoteData = subscriptionService.quoteAddonInvoice(userId, request.getQuantity());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Add-on quote generated");
            response.put("data", quoteData);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to quote add-on invoice: {}", e.getMessage(), e);
            return serverError("Failed to calculate add-on quote");
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(message));
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(message));
    }

    private ResponseEntity<Map<String, Object>> serverError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(message));
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private UUID parseUuid(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid UUID");
        }
    }

    public static class CreateInvoiceRequest {
        private String payerUserId;
        private BigDecimal amount;
        private String currency;
        private String description;
        private Object metadata;
        private String redirectSuccessUrl;
        private String redirectCancelUrl;
        private LocalDateTime expiresAt;

        public String getPayerUserId() {
            return payerUserId;
        }

        public void setPayerUserId(String payerUserId) {
            this.payerUserId = payerUserId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getMetadata() {
            return metadata;
        }

        public void setMetadata(Object metadata) {
            this.metadata = metadata;
        }

        public String getRedirectSuccessUrl() {
            return redirectSuccessUrl;
        }

        public void setRedirectSuccessUrl(String redirectSuccessUrl) {
            this.redirectSuccessUrl = redirectSuccessUrl;
        }

        public String getRedirectCancelUrl() {
            return redirectCancelUrl;
        }

        public void setRedirectCancelUrl(String redirectCancelUrl) {
            this.redirectCancelUrl = redirectCancelUrl;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    public static class InvoicePaymentRequest {
        private String method;
        private String phoneNumber;
        private String returnUrl;
        private String cancelUrl;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getReturnUrl() {
            return returnUrl;
        }

        public void setReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
        }

        public String getCancelUrl() {
            return cancelUrl;
        }

        public void setCancelUrl(String cancelUrl) {
            this.cancelUrl = cancelUrl;
        }
    }

    public static class PlanInvoiceQuoteRequest {
        private String userId;
        private String planId;
        private String billingInterval;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPlanId() {
            return planId;
        }

        public void setPlanId(String planId) {
            this.planId = planId;
        }

        public String getBillingInterval() {
            return billingInterval;
        }

        public void setBillingInterval(String billingInterval) {
            this.billingInterval = billingInterval;
        }
    }

    public static class AddonInvoiceQuoteRequest {
        private String userId;
        private Integer quantity;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
