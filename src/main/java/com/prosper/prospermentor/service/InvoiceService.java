package com.prosper.prospermentor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prosper.prospermentor.entity.Invoice;
import com.prosper.prospermentor.entity.Payment;
import com.prosper.prospermentor.entity.Subscription;
import com.prosper.prospermentor.repository.InvoiceRepository;
import com.prosper.prospermentor.repository.PaymentRepository;
import com.prosper.prospermentor.util.MpesaAccountReferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final MpesaService mpesaService;
    private final CyberSourceService cyberSourceService;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          MpesaService mpesaService,
                          CyberSourceService cyberSourceService,
                          ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.mpesaService = mpesaService;
        this.cyberSourceService = cyberSourceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Invoice createInvoice(UUID payerUserId,
                                 BigDecimal amount,
                                 String currency,
                                 String description,
                                 Object metadata,
                                 String redirectSuccessUrl,
                                 String redirectCancelUrl,
                                 LocalDateTime expiresAt) {
        return createInvoice(
                payerUserId,
                null,
                amount,
                currency,
                description,
                metadata,
                redirectSuccessUrl,
                redirectCancelUrl,
                expiresAt
        );
    }

    @Transactional
    public Invoice createInvoice(UUID payerUserId,
                                 UUID companyId,
                                 BigDecimal amount,
                                 String currency,
                                 String description,
                                 Object metadata,
                                 String redirectSuccessUrl,
                                 String redirectCancelUrl,
                                 LocalDateTime expiresAt) {
        Invoice invoice = new Invoice();
        invoice.setPayerUserId(payerUserId);
        invoice.setCompanyId(companyId);
        invoice.setAmount(amount);
        invoice.setCurrency(normalizeCurrency(currency));
        invoice.setDescription(description);
        invoice.setRedirectSuccessUrl(trimToNull(redirectSuccessUrl));
        invoice.setRedirectCancelUrl(trimToNull(redirectCancelUrl));
        invoice.setExpiresAt(expiresAt);
        invoice.setStatus(Invoice.InvoiceStatus.OPEN);
        invoice.setPublicToken(generatePublicToken());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setMetadata(serializeMetadata(metadata));
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice getByPublicToken(String publicToken) {
        return invoiceRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    @Transactional
    public Map<String, Object> initiatePayment(String publicToken,
                                               String method,
                                               String phoneNumber,
                                               String returnUrl,
                                               String cancelUrl) {
        Invoice invoice = getByPublicToken(publicToken);
        refreshExpiry(invoice);

        if (!invoice.isPayable()) {
            throw new IllegalStateException("Invoice is not payable");
        }

        String normalizedMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if ("MPESA".equals(normalizedMethod)) {
            return initiateMpesaPayment(invoice, phoneNumber);
        }

        if ("CARD".equals(normalizedMethod)) {
            return initiateCardPayment(invoice, returnUrl, cancelUrl);
        }

        throw new IllegalArgumentException("Unsupported payment method: " + method);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildPublicInvoicePayload(String publicToken) {
        Invoice invoice = getByPublicToken(publicToken);
        return buildInvoicePayload(invoice);
    }

    @Transactional
    public List<Map<String, Object>> buildUserInvoiceListPayload(UUID payerUserId) {
        List<Invoice> invoices = invoiceRepository.findByPayerUserIdOrderByCreatedAtDesc(payerUserId);
        List<Map<String, Object>> payload = new ArrayList<>();

        for (Invoice invoice : invoices) {
            payload.add(buildInvoicePayload(invoice));
        }

        return payload;
    }

    @Transactional
    public List<Map<String, Object>> buildCompanyInvoiceListPayload(UUID companyId) {
        List<Invoice> invoices = invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        List<Map<String, Object>> payload = new ArrayList<>();

        for (Invoice invoice : invoices) {
            payload.add(buildInvoicePayload(invoice));
        }

        return payload;
    }

    @Transactional
    public void markInvoicePaidByPayment(Payment payment) {
        if (payment == null || payment.getInvoiceId() == null || payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            return;
        }

        Optional<Invoice> invoiceOpt = invoiceRepository.findById(payment.getInvoiceId());
        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice {} referenced by payment {} was not found", payment.getInvoiceId(), payment.getId());
            return;
        }

        Invoice invoice = invoiceOpt.get();
        if (invoice.getStatus() == Invoice.InvoiceStatus.PAID) {
            return;
        }

        invoice.markAsPaid();
        invoiceRepository.save(invoice);
        log.info("Invoice {} marked as PAID from payment {}", invoice.getId(), payment.getId());
    }

    public String buildPaymentUrl(Invoice invoice) {
        String frontendBase = resolveFrontendBase(invoice);
        return frontendBase + "/payment/invoice/" + invoice.getPublicToken();
    }

    /**
     * Create (or reuse) an OPEN invoice that represents one subscription renewal cycle.
     * This enforces invoice-first renewal behavior before any auto-charge attempt.
     */
    @Transactional
    public Invoice createOrGetSubscriptionRenewalInvoice(Subscription subscription, String renewalMode) {
        if (subscription == null || subscription.getId() == null || subscription.getUserId() == null) {
            throw new IllegalArgumentException("subscription is required");
        }
        if (subscription.getPlan() == null) {
            throw new IllegalStateException("Subscription plan is invalid for renewal");
        }

        BigDecimal renewalAmount = subscription.getPlan().resolvePriceForInterval(subscription.getBillingInterval());
        if (renewalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Subscription plan cost is invalid for renewal");
        }

        Optional<Invoice> existingOpen = findOpenSubscriptionRenewalInvoice(
                subscription.getUserId(),
                subscription.getId()
        );
        if (existingOpen.isPresent()) {
            return existingOpen.get();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invoiceContext", "SUBSCRIPTION_RENEWAL");
        metadata.put("source", "SUBSCRIPTION_RENEWAL");
        metadata.put("subscriptionId", subscription.getId());
        metadata.put("planId", subscription.getPlan().getId());
        metadata.put("renewalMode", normalizeRenewalMode(renewalMode));
        metadata.put("billingInterval", subscription.getBillingInterval() != null
                ? subscription.getBillingInterval().name()
                : "MONTHLY");

        String description = String.format(
                "Subscription renewal - %s (%s billing)",
                subscription.getPlan().getName() != null ? subscription.getPlan().getName() : "Plan",
                subscription.getBillingInterval() == com.prosper.prospermentor.entity.BillingInterval.ANNUAL ? "annual" : "monthly"
        );

        return createInvoice(
                subscription.getUserId(),
                renewalAmount,
                subscription.getPlan().getCurrency(),
                description,
                metadata,
                null,
                null,
                LocalDateTime.now().plusDays(7)
        );
    }

    private Map<String, Object> initiateMpesaPayment(Invoice invoice, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required for MPESA payments");
        }

        Payment payment = mpesaService.initiateSTKPush(
                invoice.getPayerUserId(),
                null,
                null,
                Payment.PaymentType.INVOICE,
                invoice.getAmount(),
                phoneNumber,
                invoice.getDescription() != null ? invoice.getDescription() : "Invoice " + invoice.getInvoiceNumber()
        );

        payment.setInvoiceId(invoice.getId());
        payment.setCompanyId(invoice.getCompanyId());
        payment.setMetadata(buildInvoicePaymentMetadata(invoice));
        payment = paymentRepository.save(payment);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "MPESA");
        response.put("paymentId", payment.getId());
        response.put("checkoutRequestId", payment.getCheckoutRequestId());
        response.put("mpesaAccountReference", resolvePaymentMpesaAccountReference(payment));
        response.put("status", payment.getStatus());
        response.put("invoiceStatus", invoice.getStatus());
        return response;
    }

    private Map<String, Object> initiateCardPayment(Invoice invoice, String returnUrl, String cancelUrl) {
        Payment payment = new Payment();
        payment.setUserId(invoice.getPayerUserId());
        payment.setPayerId(invoice.getPayerUserId());
        payment.setRecipientId(invoice.getPayerUserId());
        payment.setPaymentType(Payment.PaymentType.INVOICE);
        payment.setAmount(invoice.getAmount());
        payment.setCurrency("KES");
        payment.setPaymentMethod(Payment.PaymentMethod.CARD);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setDescription(invoice.getDescription() != null ? invoice.getDescription() : "Invoice " + invoice.getInvoiceNumber());
        payment.setInvoiceId(invoice.getId());
        payment.setCompanyId(invoice.getCompanyId());
        payment.setMetadata(buildInvoicePaymentMetadata(invoice));

        Payment savedPayment = paymentRepository.save(payment);
        Map<String, String> cybersourceParams = cyberSourceService.generatePaymentParameters(
                savedPayment,
                trimToNull(returnUrl),
                trimToNull(cancelUrl)
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("method", "CARD");
        response.put("paymentId", savedPayment.getId());
        response.put("status", savedPayment.getStatus());
        response.put("invoiceStatus", invoice.getStatus());
        response.put("cybersourceEndpoint", cyberSourceService.getEndpointUrl());
        response.put("cybersourceParams", cybersourceParams);
        response.put("transactionId", cybersourceParams.get("transaction_uuid"));
        response.put("referenceNumber", cybersourceParams.get("reference_number"));
        return response;
    }

    private Map<String, Object> buildInvoicePayload(Invoice invoice) {
        refreshExpiry(invoice);
        String normalizedRedirectSuccessUrl = normalizeRedirectUrl(invoice.getRedirectSuccessUrl());
        String normalizedRedirectCancelUrl = normalizeRedirectUrl(invoice.getRedirectCancelUrl());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", invoice.getId());
        payload.put("publicToken", invoice.getPublicToken());
        payload.put("invoiceNumber", invoice.getInvoiceNumber());
        payload.put("amount", invoice.getAmount());
        payload.put("currency", invoice.getCurrency());
        payload.put("companyId", invoice.getCompanyId());
        payload.put("status", invoice.getStatus());
        payload.put("description", invoice.getDescription());
        payload.put("metadata", tryParseJson(invoice.getMetadata()));
        payload.put("redirectSuccessUrl", normalizedRedirectSuccessUrl);
        payload.put("redirectCancelUrl", normalizedRedirectCancelUrl);
        payload.put("expiresAt", invoice.getExpiresAt());
        payload.put("paidAt", invoice.getPaidAt());
        payload.put("createdAt", invoice.getCreatedAt());
        payload.put("isPayable", invoice.isPayable());
        payload.put("paymentUrl", buildPaymentUrl(invoice));
        payload.put("mpesaAccountReference", resolveInvoiceMpesaAccountReference(invoice));

        Payment latestPayment = paymentRepository.findTopByInvoiceIdOrderByCreatedAtDesc(invoice.getId()).orElse(null);
        if (latestPayment != null) {
            Map<String, Object> latestPaymentPayload = new LinkedHashMap<>();
            latestPaymentPayload.put("paymentId", latestPayment.getId());
            latestPaymentPayload.put("status", latestPayment.getStatus());
            latestPaymentPayload.put("paymentMethod", latestPayment.getPaymentMethod());
            latestPaymentPayload.put("checkoutRequestId", latestPayment.getCheckoutRequestId());
            latestPaymentPayload.put("gatewayTransactionId", latestPayment.getGatewayTransactionId());
            latestPaymentPayload.put("gatewayReference", latestPayment.getGatewayReference());
            latestPaymentPayload.put("mpesaAccountReference", resolvePaymentMpesaAccountReference(latestPayment));
            latestPaymentPayload.put("createdAt", latestPayment.getCreatedAt());
            latestPaymentPayload.put("completedAt", latestPayment.getCompletedAt());
            payload.put("latestPayment", latestPaymentPayload);
        } else {
            payload.put("latestPayment", null);
        }

        return payload;
    }

    private String resolveInvoiceMpesaAccountReference(Invoice invoice) {
        return MpesaAccountReferences.forInvoice(invoice.getId());
    }

    private String resolvePaymentMpesaAccountReference(Payment payment) {
        if (payment == null) {
            return "";
        }

        if (payment.getPaymentMethod() != Payment.PaymentMethod.MPESA) {
            return "";
        }

        if (payment.getGatewayReference() != null && payment.getGatewayReference().matches("\\d+")) {
            return payment.getGatewayReference();
        }

        return MpesaAccountReferences.forPayment(payment.getId());
    }

    private void refreshExpiry(Invoice invoice) {
        if (invoice.getStatus() == Invoice.InvoiceStatus.OPEN && invoice.isExpired()) {
            invoice.markAsExpired();
            invoiceRepository.save(invoice);
        }
    }

    private String buildInvoicePaymentMetadata(Invoice invoice) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invoiceId", invoice.getId());
        metadata.put("invoiceNumber", invoice.getInvoiceNumber());
        metadata.put("publicToken", invoice.getPublicToken());
        metadata.put("companyId", invoice.getCompanyId());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize invoice payment metadata for invoice {}", invoice.getId(), e);
            return null;
        }
    }

    private Object tryParseJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ignored) {
            return rawJson;
        }
    }

    private String serializeMetadata(Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize invoice metadata, storing as string");
            return String.valueOf(metadata);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "KES";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRenewalMode(String renewalMode) {
        if (renewalMode == null || renewalMode.isBlank()) {
            return "AUTO";
        }
        return renewalMode.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<Invoice> findOpenSubscriptionRenewalInvoice(UUID payerUserId, UUID subscriptionId) {
        List<Invoice> invoices = invoiceRepository.findByPayerUserIdOrderByCreatedAtDesc(payerUserId);
        for (Invoice invoice : invoices) {
            if (invoice.getStatus() != Invoice.InvoiceStatus.OPEN) {
                continue;
            }
            if (invoice.isExpired()) {
                invoice.markAsExpired();
                invoiceRepository.save(invoice);
                continue;
            }
            if (isSubscriptionRenewalInvoice(invoice, subscriptionId)) {
                return Optional.of(invoice);
            }
        }
        return Optional.empty();
    }

    private boolean isSubscriptionRenewalInvoice(Invoice invoice, UUID subscriptionId) {
        String metadataRaw = invoice.getMetadata();
        if (metadataRaw == null || metadataRaw.isBlank()) {
            return false;
        }

        try {
            JsonNode metadata = objectMapper.readTree(metadataRaw);
            String context = readMetadataText(metadata, "invoiceContext", "context", "paymentContext");
            if (context == null || !"SUBSCRIPTION_RENEWAL".equalsIgnoreCase(context.trim())) {
                return false;
            }

            String metadataSubscriptionId = readMetadataText(metadata, "subscriptionId");
            if (metadataSubscriptionId == null || metadataSubscriptionId.isBlank()) {
                return false;
            }

            return subscriptionId.toString().equalsIgnoreCase(metadataSubscriptionId.trim());
        } catch (Exception ignored) {
            return false;
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

    private String generatePublicToken() {
        // 32-char URL-safe token from UUID
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "INV-" + datePart + "-" + suffix;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private String resolveFrontendBase(Invoice invoice) {
        String successOrigin = extractOrigin(normalizeRedirectUrl(invoice != null ? invoice.getRedirectSuccessUrl() : null));
        if (successOrigin != null) {
            return successOrigin;
        }

        String cancelOrigin = extractOrigin(normalizeRedirectUrl(invoice != null ? invoice.getRedirectCancelUrl() : null));
        if (cancelOrigin != null) {
            return cancelOrigin;
        }

        return trimTrailingSlash(frontendUrl);
    }

    private String extractOrigin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }

            int port = uri.getPort();
            if (port > 0) {
                return uri.getScheme() + "://" + uri.getHost() + ":" + port;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception ex) {
            log.warn("Unable to extract origin from URL '{}': {}", url, ex.getMessage());
            return null;
        }
    }

    private String normalizeRedirectUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String trimmed = url.trim();
        String configuredOrigin = extractOrigin(frontendUrl);
        String configuredHost = extractHost(frontendUrl);
        if (configuredOrigin == null || configuredHost == null || isLocalHost(configuredHost)) {
            return trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String redirectHost = uri.getHost();
            if (redirectHost == null || !isLocalHost(redirectHost)) {
                return trimmed;
            }

            String path = uri.getRawPath() != null ? uri.getRawPath() : "";
            String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
            String fragment = uri.getRawFragment() != null ? "#" + uri.getRawFragment() : "";
            return configuredOrigin + path + query + fragment;
        } catch (Exception ex) {
            log.warn("Unable to normalize redirect URL '{}': {}", url, ex.getMessage());
            return trimmed;
        }
    }

    private String extractHost(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(url.trim());
            return uri.getHost();
        } catch (Exception ex) {
            log.warn("Unable to extract host from URL '{}': {}", url, ex.getMessage());
            return null;
        }
    }

    private boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
