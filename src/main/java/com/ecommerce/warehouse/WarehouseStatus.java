package com.ecommerce.warehouse;

import java.util.Locale;

/** Lifecycle state of a warehouse / fulfillment location. */
public enum WarehouseStatus {
    ACTIVE,
    INACTIVE;

    public static WarehouseStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return WarehouseStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
