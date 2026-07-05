package com.ecommerce.promotion;

import java.math.BigDecimal;
import java.util.List;

public record PromotionApplyData(
        boolean found,
        boolean qualifies,
        String reason,
        String promotionPublicId,
        String code,
        String name,
        String type,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        boolean freeShipping,
        BigDecimal shippingSavings,
        BigDecimal finalShippingCharge,
        BigDecimal finalTotal,
        List<PromotionAppliedItemData> appliedItems,
        String summary) {}
