package com.ecommerce.bundle;

import java.util.Locale;

/**
 * A bundle groups several DIFFERENT products; a multi-pack groups N units of a
 * single product. Both share the same composite structure, so one type flag
 * distinguishes them.
 */
public enum BundleType {
    BUNDLE,
    MULTIPACK;

    public static BundleType from(String value) {
        if (value == null || value.isBlank()) {
            return BUNDLE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return BundleType.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
