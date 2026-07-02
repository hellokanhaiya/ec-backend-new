package com.ecommerce.product;

public record ProductImageRequest(String url, String altText, Integer position, Boolean isPrimary) {}
