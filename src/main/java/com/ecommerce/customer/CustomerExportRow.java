package com.ecommerce.customer;

import java.math.BigDecimal;

/** One flattened customer row for CSV export (default address inlined). */
public record CustomerExportRow(
        String customerCode,
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone,
        boolean acceptsEmail,
        boolean acceptsSms,
        boolean acceptsWhatsapp,
        boolean acceptsPromos,
        String status,
        BigDecimal wallet,
        Integer rewardPoints,
        BigDecimal storeCredit,
        String tags,
        String note,
        String company,
        String addressLine1,
        String addressLine2,
        String city,
        String province,
        String countryCode,
        String zip,
        String addressPhone,
        BigDecimal totalSpent,
        int totalOrders) {}
