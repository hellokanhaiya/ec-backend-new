package com.ecommerce.shipping;

import java.math.BigDecimal;

public record ShippingRateTierData(
        Long id, BigDecimal lowerBound, BigDecimal upperBound, BigDecimal price, BigDecimal perUnitPrice) {}
