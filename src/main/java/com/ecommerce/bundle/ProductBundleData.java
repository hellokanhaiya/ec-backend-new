package com.ecommerce.bundle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductBundleData(
        String publicBundleId,
        String bundleCode,
        String type,
        String status,
        String name,
        String sku,
        String image,
        String description,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal componentsValue,
        BigDecimal componentsCost,
        BigDecimal savings,
        BigDecimal margin,
        int componentCount,
        int totalUnits,
        int availablePacks,
        String currencyCode,
        String currencySymbol,
        List<ProductBundleItemData> items,
        Instant createdAt,
        Instant updatedAt) {}
