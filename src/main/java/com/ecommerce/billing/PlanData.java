package com.ecommerce.billing;

import java.math.BigDecimal;
import java.util.List;

public record PlanData(
        String code,
        String name,
        String tagline,
        BigDecimal monthlyPrice,
        BigDecimal yearlyPrice,
        String currencyCode,
        String currencySymbol,
        int credits,
        boolean highlighted,
        int sortOrder,
        List<String> features,
        List<String> industries) {}
