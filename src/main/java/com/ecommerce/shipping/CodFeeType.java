package com.ecommerce.shipping;

import java.util.Locale;

/** How a Cash-on-Delivery handling fee is charged. */
public enum CodFeeType {
    FLAT,
    PERCENT;

    public static CodFeeType from(String value) {
        if (value == null || value.isBlank()) {
            return FLAT;
        }
        try {
            return CodFeeType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FLAT;
        }
    }
}
