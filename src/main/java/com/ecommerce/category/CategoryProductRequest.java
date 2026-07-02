package com.ecommerce.category;

import java.math.BigDecimal;

public record CategoryProductRequest(
        String publicProductId,
        String name,
        String sku,
        String image,
        BigDecimal price,
        BigDecimal mrp,
        Integer position) {}
