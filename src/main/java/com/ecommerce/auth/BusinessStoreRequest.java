package com.ecommerce.auth;

public record BusinessStoreRequest(
        String publicUserId,
        String audience,
        String businessName,
        String categoryKey,
        String categoryLabel,
        String customCategory,
        String currencyCode,
        String countryCode,
        String countryName) {}
