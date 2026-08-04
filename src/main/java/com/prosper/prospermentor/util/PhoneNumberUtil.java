package com.prosper.prospermentor.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Utility helpers for normalizing and validating WhatsApp recipient phone numbers.
 */
public final class PhoneNumberUtil {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneNumberUtil() {
    }

    /**
     * Normalize to E.164 format and return null if the value cannot be normalized.
     */
    public static String normalizeToE164(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }

        String cleaned = phoneNumber.trim().replaceAll("[^0-9+]", "");
        String candidate;

        if (cleaned.startsWith("+")) {
            String digitsOnly = cleaned.substring(1).replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) {
                return null;
            }
            candidate = "+" + digitsOnly;
        } else {
            String digitsOnly = cleaned.replaceAll("[^0-9]", "");
            if (digitsOnly.isEmpty()) {
                return null;
            }

            // Preserve existing behavior for local-format numbers.
            if (digitsOnly.startsWith("0")) {
                candidate = "+254" + digitsOnly.substring(1);
            } else if (digitsOnly.startsWith("254")) {
                candidate = "+" + digitsOnly;
            } else {
                candidate = "+254" + digitsOnly;
            }
        }

        return isValidE164(candidate) ? candidate : null;
    }

    public static boolean isValidE164(String phoneNumber) {
        return StringUtils.hasText(phoneNumber) && E164_PATTERN.matcher(phoneNumber).matches();
    }
}
