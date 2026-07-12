package com.ecommerce.bundle;

import java.math.BigDecimal;
import java.util.List;

public record ProductBundleRequest(
        String type,
        String status,
        String name,
        String sku,
        String image,
        String description,
        BigDecimal price,
        BigDecimal compareAtPrice,
        List<ProductBundleItemRequest> items) {}
