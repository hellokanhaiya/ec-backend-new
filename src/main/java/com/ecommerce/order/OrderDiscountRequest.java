package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderDiscountRequest(
        String mode,
        String code,
        String type,
        BigDecimal value,
        String reason) {}
