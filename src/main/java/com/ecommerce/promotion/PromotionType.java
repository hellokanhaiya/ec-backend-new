package com.ecommerce.promotion;

import java.util.Locale;

public enum PromotionType {
    AMOUNT_OFF_PRODUCTS,
    BUY_X_GET_Y,
    AMOUNT_OFF_ORDER,
    FREE_SHIPPING;

    public static PromotionType from(String value) {
        if (value == null || value.isBlank()) {
            return AMOUNT_OFF_PRODUCTS;
        }
        return PromotionType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
