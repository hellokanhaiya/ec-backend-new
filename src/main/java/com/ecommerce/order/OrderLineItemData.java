package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderLineItemData(
        String productPublicId,
        String name,
        String sku,
        String variant,
        int quantity,
        BigDecimal price,
        BigDecimal lineTotal,
        boolean taxable,
        BigDecimal taxRate,
        String image,
        String discountType,
        BigDecimal discountValue,
        String discountReason,
        BigDecimal discountAmount) {}
