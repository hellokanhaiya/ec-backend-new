package com.ecommerce.shipping;

import java.math.BigDecimal;

public record DeliverySettingsData(
        int processingDays,
        String originPincode,
        BigDecimal freeShippingThreshold,
        boolean codEnabled,
        String codFeeType,
        BigDecimal codFeeValue,
        BigDecimal codMinOrder,
        BigDecimal codMaxOrder) {}
