package com.ecommerce.order;

import java.util.Locale;

public enum DiscountType {
    FIXED,
    PERCENTAGE;

    public static DiscountType from(String value) {
        if (value == null || value.isBlank()) {
            return FIXED;
        }
        return DiscountType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
