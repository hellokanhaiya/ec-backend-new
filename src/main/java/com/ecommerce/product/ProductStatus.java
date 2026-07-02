package com.ecommerce.product;

/** Publishing state of a product, mirroring the common e-commerce lifecycle
 * (Shopify: active / draft / archived). Defaults to DRAFT so a half-filled
 * product never accidentally goes live. */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    public static ProductStatus from(String value) {
        if (value == null || value.isBlank()) {
            return DRAFT;
        }
        try {
            return ProductStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DRAFT;
        }
    }
}
