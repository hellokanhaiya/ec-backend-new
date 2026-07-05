package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionTargetMode {
    ALL,
    PRODUCTS,
    COLLECTIONS,
    CATEGORIES;

    public static PromotionTargetMode from(String value) {
        if (value == null || value.isBlank()) {
            return PRODUCTS;
        }
        return PromotionTargetMode.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
