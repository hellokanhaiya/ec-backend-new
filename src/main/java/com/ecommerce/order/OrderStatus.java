package com.ecommerce.order;

import java.util.Locale;

public enum OrderStatus {
    DRAFT,
    CONFIRMED;

    public static OrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            return CONFIRMED;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return OrderStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
