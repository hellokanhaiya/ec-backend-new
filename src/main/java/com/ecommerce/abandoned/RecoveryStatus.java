package com.ecommerce.abandoned;

import java.util.Locale;

/** Lifecycle of an abandoned cart from the store's recovery perspective. */
public enum RecoveryStatus {
    /** Cart was started but not checked out; still recoverable. */
    ACTIVE,
    /** Shopper returned and completed the purchase. */
    RECOVERED,
    /** Cart is considered unrecoverable (too old / opted out). */
    LOST;

    public static RecoveryStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return RecoveryStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
