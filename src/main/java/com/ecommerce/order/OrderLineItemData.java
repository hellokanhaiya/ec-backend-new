package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderLineItemData(
        String id,
        String productPublicId,
        String name,
        String sku,
        String variant,
        int quantity,
        BigDecimal price,
        BigDecimal lineTotal,
        boolean taxable,
        BigDecimal taxRate,
        String image) {}
