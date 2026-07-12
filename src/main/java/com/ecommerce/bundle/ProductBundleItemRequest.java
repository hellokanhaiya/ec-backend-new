package com.ecommerce.bundle;

public record ProductBundleItemRequest(
        String productPublicId,
        String name,
        String sku,
        String image,
        Integer quantity) {}
