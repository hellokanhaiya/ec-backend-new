package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderLineItemRequest(
        String productPublicId,
        String name,
        String sku,
        String variant,
        Integer quantity,
        BigDecimal price,
        Boolean taxable,
        BigDecimal taxRate,
        String image,
        OrderLineItemDiscountRequest discount) {}
