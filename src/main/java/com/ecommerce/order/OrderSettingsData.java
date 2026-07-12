package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderSettingsData(
        String storeId,
        String orderPrefix,
        String draftPrefix,
        Integer orderNumberPadding,
        boolean financialYearReset,
        boolean includeFinancialYear,
        Integer financialYearStartMonth,
        BigDecimal defaultShippingCharge,
        BigDecimal defaultPackageCharge,
        BigDecimal freeShippingThreshold,
        BigDecimal defaultTaxRate,
        /** Preview of the next order number for the current period, e.g. "ORD-2526-00001". */
        String nextOrderNumberPreview,
        /** Preview of the next draft number for the current period, e.g. "DR-0001". */
        String nextDraftNumberPreview) {}
