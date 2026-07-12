package com.ecommerce.analytics;

public record TopProductData(
        String name,
        int sales,
        long revenue,
        int stock,
        String category) {}
