package com.ecommerce.bundle;

import java.math.BigDecimal;

public record ProductBundleItemData(
        String productPublicId,
        String name,
        String sku,
        String image,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal lineTotal,
        int availableStock,
        int packsSupported) {}
