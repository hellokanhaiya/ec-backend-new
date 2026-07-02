package com.ecommerce.product;

public record ProductImageData(
        Long id, String url, String altText, int position, boolean isPrimary) {}
