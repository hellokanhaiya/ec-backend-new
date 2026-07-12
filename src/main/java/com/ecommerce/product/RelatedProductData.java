package com.ecommerce.product;

import java.math.BigDecimal;

public record RelatedProductData(
        String publicProductId,
        String name,
        String sku,
        String image,
        BigDecimal price,
        BigDecimal mrp,
        int position) {}
