package com.prosper.prospermentor.util;

import java.util.UUID;

/**
 * Generates short numeric M-Pesa account references that fit Safaricom's
 * AccountReference limit while preserving a stable link to internal records.
 */
public final class MpesaAccountReferences {

    private static final long SEVEN_DIGIT_MODULUS = 10_000_000L;

    private MpesaAccountReferences() {
    }

    public static String forPayment(UUID paymentId) {
        return generate("1", paymentId);
    }

    public static String forInvoice(UUID invoiceId) {
        return generate("2", invoiceId);
    }

    private static String generate(String prefix, UUID id) {
        if (id == null) {
            return "";
        }

        long mixed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        long suffix = Math.floorMod(mixed, SEVEN_DIGIT_MODULUS);
        return prefix + String.format("%07d", suffix);
    }
}
