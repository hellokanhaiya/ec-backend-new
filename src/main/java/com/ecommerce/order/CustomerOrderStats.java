package com.ecommerce.order;

import java.math.BigDecimal;

/**
 * Aggregated order figures for a single customer, used to populate the customer
 * list / export (order count and lifetime spend). Draft orders are excluded by
 * the query that produces these rows.
 */
public interface CustomerOrderStats {
    String getCustomerPublicId();

    long getOrderCount();

    BigDecimal getTotalSpent();
}
