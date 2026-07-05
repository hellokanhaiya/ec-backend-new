package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ARCHIVED;

    public static PromotionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        return PromotionStatus.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
