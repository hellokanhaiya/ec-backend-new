package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderLineItemDiscountRequest(
        String type,
        BigDecimal value,
        String reason) {}
