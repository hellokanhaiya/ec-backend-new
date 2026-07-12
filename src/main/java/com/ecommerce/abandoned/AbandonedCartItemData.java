package com.ecommerce.abandoned;

import java.math.BigDecimal;

public record AbandonedCartItemData(
        String productPublicId,
        String name,
        String variant,
        String image,
        int quantity,
        BigDecimal price) {}
