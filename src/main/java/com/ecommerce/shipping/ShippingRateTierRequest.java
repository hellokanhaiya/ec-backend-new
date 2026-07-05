package com.ecommerce.shipping;

import java.math.BigDecimal;

public record ShippingRateTierRequest(
        BigDecimal lowerBound, BigDecimal upperBound, BigDecimal price, BigDecimal perUnitPrice) {}
