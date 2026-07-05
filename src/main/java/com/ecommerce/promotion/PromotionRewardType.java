package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionRewardType {
    FREE,
    PERCENTAGE,
    FIXED;

    public static PromotionRewardType from(String value) {
        if (value == null || value.isBlank()) {
            return FREE;
        }
        return PromotionRewardType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
