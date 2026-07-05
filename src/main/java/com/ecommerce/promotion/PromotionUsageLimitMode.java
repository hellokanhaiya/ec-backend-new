package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionUsageLimitMode {
    UNLIMITED,
    TOTAL,
    PER_CUSTOMER;

    public static PromotionUsageLimitMode from(String value) {
        if (value == null || value.isBlank()) {
            return UNLIMITED;
        }
        return PromotionUsageLimitMode.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
