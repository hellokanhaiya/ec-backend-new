package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionMethod {
    CODE,
    AUTOMATIC;

    public static PromotionMethod from(String value) {
        if (value == null || value.isBlank()) {
            return CODE;
        }
        return PromotionMethod.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
