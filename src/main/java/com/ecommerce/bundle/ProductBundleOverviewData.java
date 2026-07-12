package com.ecommerce.bundle;

import java.math.BigDecimal;

public record ProductBundleOverviewData(
        long total,
        long active,
        long draft,
        long archived,
        long bundles,
        long multipacks,
        long outOfStock,
        BigDecimal totalSavings) {}
