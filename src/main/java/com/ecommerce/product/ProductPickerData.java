package com.ecommerce.product;

import java.math.BigDecimal;

public record ProductPickerData(
        String publicProductId,
        String title,
        String sku,
        String image,
        BigDecimal price,
        Integer stock,
        boolean taxable,
        BigDecimal taxRate,
        String vendor,
        String category) {}
