package com.ecommerce.order;

import java.util.Locale;

public enum DiscountMode {
    MANUAL,
    COUPON;

    public static DiscountMode from(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        return DiscountMode.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
