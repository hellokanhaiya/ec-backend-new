package com.ecommerce.product;

/** Fulfilment nature of a product. PHYSICAL ships; DIGITAL/SERVICE do not, which
 * lets the frontend hide shipping/dimension fields. Defaults to PHYSICAL. */
public enum ProductType {
    PHYSICAL,
    DIGITAL,
    SERVICE;

    public static ProductType from(String value) {
        if (value == null || value.isBlank()) {
            return PHYSICAL;
        }
        try {
            return ProductType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PHYSICAL;
        }
    }
}
