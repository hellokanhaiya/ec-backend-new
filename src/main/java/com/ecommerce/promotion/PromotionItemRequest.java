package com.ecommerce.promotion;

import java.math.BigDecimal;

public record PromotionItemRequest(String productPublicId, Integer quantity, BigDecimal unitPrice) {}
