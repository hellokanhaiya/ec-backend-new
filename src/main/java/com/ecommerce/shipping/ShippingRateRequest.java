package com.ecommerce.shipping;

import java.math.BigDecimal;
import java.util.List;

public record ShippingRateRequest(
        String zonePublicId,
        String name,
        String rateType,
        BigDecimal basePrice,
        BigDecimal perUnitPrice,
        BigDecimal minWeight,
        BigDecimal maxWeight,
        BigDecimal minSubtotal,
        BigDecimal maxSubtotal,
        Integer etaMinDays,
        Integer etaMaxDays,
        Boolean active,
        List<ShippingRateTierRequest> tiers) {}
