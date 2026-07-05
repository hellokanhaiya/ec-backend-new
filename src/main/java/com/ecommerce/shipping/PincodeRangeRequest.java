package com.ecommerce.shipping;

import java.math.BigDecimal;

public record PincodeRangeRequest(
        String zonePublicId,
        String warehousePublicId,
        String fromPincode,
        String toPincode,
        Boolean codAvailable,
        Integer etaMinDays,
        Integer etaMaxDays,
        BigDecimal distanceKm) {}
