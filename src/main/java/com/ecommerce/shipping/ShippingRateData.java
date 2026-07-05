package com.ecommerce.shipping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShippingRateData(
        String publicRateId,
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
        boolean active,
        List<ShippingRateTierData> tiers,
        Instant createdAt,
        Instant updatedAt) {}
