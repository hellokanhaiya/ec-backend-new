package com.ecommerce.purchase;

import java.util.Locale;

public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    RECEIVED,
    CANCELLED;

    public static PurchaseOrderStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return PurchaseOrderStatus.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
