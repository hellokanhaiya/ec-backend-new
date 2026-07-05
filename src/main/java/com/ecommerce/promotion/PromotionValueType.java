package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionValueType {
    FIXED,
    PERCENTAGE;

    public static PromotionValueType from(String value) {
        if (value == null || value.isBlank()) {
            return FIXED;
        }
        return PromotionValueType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
