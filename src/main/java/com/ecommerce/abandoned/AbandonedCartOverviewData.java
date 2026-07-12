package com.ecommerce.abandoned;

import java.math.BigDecimal;

public record AbandonedCartOverviewData(
        long totalCarts,
        long activeCarts,
        long recoveredCarts,
        long lostCarts,
        BigDecimal totalValue,
        BigDecimal recoverableValue,
        double recoveryRate) {}
