package com.prosper.prospermentor.specification;

import com.prosper.prospermentor.entity.Payment;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.Locale;
import java.util.UUID;

public final class PaymentSpecification {

    private PaymentSpecification() {
    }

    public static Specification<Payment> hasUserId(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Payment> hasStatus(Payment.PaymentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasPaymentMethod(Payment.PaymentMethod paymentMethod) {
        return (root, query, cb) -> paymentMethod == null ? null : cb.equal(root.get("paymentMethod"), paymentMethod);
    }

    public static Specification<Payment> hasPaymentType(Payment.PaymentType paymentType) {
        return (root, query, cb) -> paymentType == null ? null : cb.equal(root.get("paymentType"), paymentType);
    }

    public static Specification<Payment> hasInvoiceId(UUID invoiceId) {
        return (root, query, cb) -> invoiceId == null ? null : cb.equal(root.get("invoiceId"), invoiceId);
    }

    public static Specification<Payment> hasCompanyId(UUID companyId) {
        return (root, query, cb) -> companyId == null ? null : cb.equal(root.get("companyId"), companyId);
    }

    public static Specification<Payment> hasSessionId(UUID sessionId) {
        return (root, query, cb) -> sessionId == null ? null : cb.equal(root.get("sessionId"), sessionId);
    }

    public static Specification<Payment> hasSubscriptionId(UUID subscriptionId) {
        return (root, query, cb) -> subscriptionId == null ? null : cb.equal(root.get("subscriptionId"), subscriptionId);
    }

    public static Specification<Payment> search(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(lowerOrEmpty(root.get("description").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("checkoutRequestId").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("merchantRequestId").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("mpesaReceiptNumber").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("gatewayTransactionId").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("gatewayReference").as(String.class), cb), pattern),
                cb.like(lowerOrEmpty(root.get("phoneNumber").as(String.class), cb), pattern)
        );
    }

    public static Specification<Payment> filter(UUID userId,
                                                Payment.PaymentStatus status,
                                                Payment.PaymentMethod paymentMethod,
                                                Payment.PaymentType paymentType,
                                                UUID companyId,
                                                UUID invoiceId,
                                                UUID sessionId,
                                                UUID subscriptionId,
                                                String search) {
        return Specification.allOf(
                hasUserId(userId),
                hasStatus(status),
                hasPaymentMethod(paymentMethod),
                hasPaymentType(paymentType),
                hasCompanyId(companyId),
                hasInvoiceId(invoiceId),
                hasSessionId(sessionId),
                hasSubscriptionId(subscriptionId),
                search(search)
        );
    }

    private static Expression<String> lowerOrEmpty(Expression<String> expression, CriteriaBuilder cb) {
        return cb.lower(cb.coalesce(expression, ""));
    }
}
