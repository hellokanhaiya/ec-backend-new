package com.ecommerce.auth;

public record BusinessStoreData(
        String publicUserId,
        String orgId,
        String storeId,
        String businessName,
        String categoryKey,
        String categoryLabel,
        String customCategory,
        String currencyCode,
        String countryCode,
        String countryName,
        String audience) {}
