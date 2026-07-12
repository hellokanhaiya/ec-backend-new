package com.ecommerce.bundle;

import java.util.Locale;

public enum BundleStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    public static BundleStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return BundleStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
