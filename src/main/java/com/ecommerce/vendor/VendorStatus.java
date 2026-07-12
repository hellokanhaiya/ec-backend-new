package com.ecommerce.vendor;

import java.util.Locale;

/** Lifecycle of a marketplace vendor from the store's approval perspective. */
public enum VendorStatus {
    /** Applied / added but not yet approved to sell. */
    PENDING,
    /** Approved — their products can be sold and orders attributed to them. */
    APPROVED,
    /** Temporarily blocked from selling. */
    SUSPENDED;

    public static VendorStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return VendorStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
