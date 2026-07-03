package com.ecommerce.media;

public record MediaProductData(
        String publicProductId,
        String title,
        String sku,
        String category,
        String image) {}
