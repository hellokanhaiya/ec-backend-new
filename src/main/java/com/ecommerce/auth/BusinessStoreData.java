package com.ecommerce.auth;

public record BusinessStoreData(
        String publicUserId,
        String orgId,
        String storeId,
        String businessName,
        String legalName,
        String adminEmail,
        String adminPhone,
        String categoryKey,
        String categoryLabel,
        String customCategory,
        String currencyCode,
        String countryCode,
        String countryName,
        String taxNumber,
        String licenseKey,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String timeZone,
        String dateFormat,
        String businessEmail,
        String businessPhone,
        String audience) {}

