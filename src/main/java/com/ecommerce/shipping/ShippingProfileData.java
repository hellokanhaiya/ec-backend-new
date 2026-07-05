package com.ecommerce.shipping;

import java.time.Instant;
import java.util.List;

public record ShippingProfileData(
        String publicProfileId,
        String name,
        boolean defaultProfile,
        List<ShippingZoneData> zones,
        Instant createdAt,
        Instant updatedAt) {}
