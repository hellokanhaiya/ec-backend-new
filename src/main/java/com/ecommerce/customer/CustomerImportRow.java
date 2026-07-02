package com.ecommerce.customer;

/**
 * One raw CSV row for import. All values arrive as strings and are validated /
 * parsed server-side (booleans as yes/no/true/false/1/0; numbers as decimals).
 * {@code rowNumber} is the 1-based data row used to report errors back.
 */
public record CustomerImportRow(
        Integer rowNumber,
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone,
        String acceptsEmail,
        String acceptsSms,
        String acceptsWhatsapp,
        String acceptsPromos,
        String status,
        String wallet,
        String rewardPoints,
        String storeCredit,
        String tags,
        String note,
        String company,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String countryCode,
        String zip,
        String addressPhone) {}
