package com.ecommerce.bundle;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductBundleSummaryData(
        String publicBundleId,
        String bundleCode,
        String type,
        String status,
        String name,
        String sku,
        String image,
        BigDecimal price,
        BigDecimal componentsValue,
        BigDecimal savings,
        int componentCount,
        int totalUnits,
        int availablePacks,
        Instant createdAt,
        Instant updatedAt) {}
