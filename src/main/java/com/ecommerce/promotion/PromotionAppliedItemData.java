package com.ecommerce.promotion;

import java.math.BigDecimal;

public record PromotionAppliedItemData(
        String productPublicId,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount) {}
