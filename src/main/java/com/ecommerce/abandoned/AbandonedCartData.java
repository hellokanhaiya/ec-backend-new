package com.ecommerce.abandoned;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AbandonedCartData(
        String id,
        String customer,
        String email,
        String phone,
        String channel,
        String status,
        int recoveryEmailsSent,
        String checkoutUrl,
        int items,
        BigDecimal subtotal,
        BigDecimal cartValue,
        String currencyCode,
        String currencySymbol,
        List<AbandonedCartItemData> products,
        Instant lastActivityAt,
        Instant createdAt,
        Instant updatedAt) {}
