package com.ecommerce.purchase;

import java.math.BigDecimal;

public record PurchaseOrderLineItemData(
        String id,
        String productPublicId,
        String name,
        String sku,
        String variant,
        int quantity,
        BigDecimal costPrice,
        BigDecimal lineTotal,
        String image) {}
