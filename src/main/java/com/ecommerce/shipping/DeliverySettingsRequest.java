package com.ecommerce.shipping;

import java.math.BigDecimal;

public record DeliverySettingsRequest(
        Integer processingDays,
        String originPincode,
        BigDecimal freeShippingThreshold,
        Boolean codEnabled,
        String codFeeType,
        BigDecimal codFeeValue,
        BigDecimal codMinOrder,
        BigDecimal codMaxOrder) {}
