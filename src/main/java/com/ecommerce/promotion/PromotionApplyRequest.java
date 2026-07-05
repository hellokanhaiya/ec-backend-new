package com.ecommerce.promotion;

import java.math.BigDecimal;
import java.util.List;

public record PromotionApplyRequest(
        String code,
        String customerPublicId,
        BigDecimal shippingCharge,
        List<PromotionItemRequest> items) {}
