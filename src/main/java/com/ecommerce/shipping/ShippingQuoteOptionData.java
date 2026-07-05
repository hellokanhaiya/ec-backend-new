package com.ecommerce.shipping;

import java.math.BigDecimal;

/** A single available shipping method with its computed price and delivery window. */
public record ShippingQuoteOptionData(
        String ratePublicId,
        String zonePublicId,
        String name,
        String rateType,
        BigDecimal price,
        String currencyCode,
        Integer etaMinDays,
        Integer etaMaxDays,
        boolean codAvailable) {}
