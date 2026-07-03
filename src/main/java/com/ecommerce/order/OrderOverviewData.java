package com.ecommerce.order;

import java.math.BigDecimal;

public record OrderOverviewData(
        long totalOrders,
        long pendingPayment,
        long readyToShip,
        long shipped,
        long delivered,
        long returns,
        BigDecimal revenue) {}
