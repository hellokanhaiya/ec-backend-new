package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderSettingsRequest(
        String orderPrefix,
        Integer orderNumberPadding,
        Boolean financialYearReset,
        Boolean includeFinancialYear,
        Integer financialYearStartMonth,
        BigDecimal defaultShippingCharge,
        BigDecimal defaultPackageCharge,
        BigDecimal freeShippingThreshold,
        BigDecimal defaultTaxRate) {}
