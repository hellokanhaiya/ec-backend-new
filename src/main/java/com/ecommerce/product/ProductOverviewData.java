package com.ecommerce.product;

import java.math.BigDecimal;

/** KPI aggregates for the products page header. Fetched once and kept in sync
 * client-side on create/update/delete, like the customer overview. */
public record ProductOverviewData(
        long totalProducts,
        long active,
        long draft,
        long archived,
        long lowStock,
        long categories,
        BigDecimal avgPrice) {}
