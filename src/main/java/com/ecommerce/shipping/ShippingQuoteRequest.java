package com.ecommerce.shipping;

import java.math.BigDecimal;

/**
 * A request to quote available shipping methods to a destination. Provide the destination
 * geography (at minimum {@code country} and {@code pincode}) and the cart's weight/subtotal.
 * {@code warehousePublicId} is the origin for distance-based rates (optional — defaults to
 * the store's default warehouse).
 */
public record ShippingQuoteRequest(
        String country,
        String state,
        String city,
        String pincode,
        BigDecimal cartWeight,
        BigDecimal cartSubtotal,
        String warehousePublicId,
        String profilePublicId) {}
