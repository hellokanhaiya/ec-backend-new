package com.ecommerce.vendor;

import java.util.Locale;

/** How the marketplace commission for a vendor is calculated. */
public enum CommissionType {
    /** A percentage of each sale (0–100). */
    PERCENTAGE,
    /** A flat amount deducted per order/line. */
    FIXED;

    public static CommissionType from(String value) {
        if (value == null || value.isBlank()) {
            return PERCENTAGE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return CommissionType.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
