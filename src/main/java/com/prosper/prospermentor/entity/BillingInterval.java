package com.prosper.prospermentor.entity;

import java.util.Locale;

public enum BillingInterval {
    MONTHLY,
    ANNUAL;

    public static BillingInterval fromString(String value) {
        if (value == null || value.isBlank()) {
            return MONTHLY;
        }

        try {
            return BillingInterval.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MONTHLY;
        }
    }
}
