package com.ecommerce.auth;

public record BusinessStoreRequest(
        String publicUserId,
        String audience,
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
        String businessPhone) {}

