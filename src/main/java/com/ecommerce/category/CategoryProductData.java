package com.ecommerce.category;

import java.math.BigDecimal;

public record CategoryProductData(
        String publicProductId,
        String name,
        String sku,
        String image,
        BigDecimal price,
        BigDecimal mrp,
        int position) {}
