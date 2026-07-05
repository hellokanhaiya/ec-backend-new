package com.ecommerce.shipping;

import java.math.BigDecimal;
import java.time.Instant;

public record PincodeRangeData(
        String publicPincodeId,
        String zonePublicId,
        String warehousePublicId,
        String fromPincode,
        String toPincode,
        boolean codAvailable,
        Integer etaMinDays,
        Integer etaMaxDays,
        BigDecimal distanceKm,
        Instant createdAt,
        Instant updatedAt) {}
