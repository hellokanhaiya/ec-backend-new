package com.ecommerce.product;

import java.math.BigDecimal;

public record RelatedProductRequest(
        String publicProductId,
        String name,
        String sku,
        String image,
        BigDecimal price,
        BigDecimal mrp,
        Integer position) {}
