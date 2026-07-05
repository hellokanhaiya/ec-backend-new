package com.ecommerce.shipping;

import java.time.Instant;
import java.util.List;

public record ShippingZoneData(
        String publicZoneId,
        String profilePublicId,
        String name,
        String currencyCode,
        List<ShippingRegionData> regions,
        List<ShippingRateData> rates,
        Instant createdAt,
        Instant updatedAt) {}
