package com.ecommerce.shipping;

import java.util.Locale;

/**
 * How a shipping rate's price is computed.
 * <ul>
 *   <li>{@code FREE} — always 0.</li>
 *   <li>{@code FLAT} — a fixed {@code basePrice}.</li>
 *   <li>{@code WEIGHT_BASED} — {@code basePrice + perUnitPrice × cartWeight}, or tier bands on weight.</li>
 *   <li>{@code PRICE_BASED} — tier bands on the cart subtotal.</li>
 *   <li>{@code DISTANCE_BASED} — {@code basePrice + perUnitPrice × km} from warehouse to destination.</li>
 * </ul>
 */
public enum RateType {
    FREE,
    FLAT,
    WEIGHT_BASED,
    PRICE_BASED,
    DISTANCE_BASED;

    public static RateType from(String value) {
        if (value == null || value.isBlank()) {
            return FLAT;
        }
        try {
            return RateType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return FLAT;
        }
    }
}
