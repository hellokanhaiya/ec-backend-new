package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionMinimumRequirement {
    NONE,
    AMOUNT,
    QUANTITY;

    public static PromotionMinimumRequirement from(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return PromotionMinimumRequirement.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
