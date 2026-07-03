package com.ecommerce.order;

import java.util.Locale;

public enum FulfillmentStatus {
    UNFULFILLED,
    PENDING,
    READY_TO_SHIP,
    SHIPPED,
    FULFILLED,
    DELIVERED,
    RETURNED,
    CANCELLED;

    public static FulfillmentStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return FulfillmentStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
