package com.ecommerce.purchase;

import java.math.BigDecimal;

public record PurchaseOrderLineItemRequest(
        String productPublicId,
        String name,
        String sku,
        String variant,
        Integer quantity,
        BigDecimal costPrice,
        String image) {}
