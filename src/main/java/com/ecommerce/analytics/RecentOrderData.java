package com.ecommerce.analytics;

public record RecentOrderData(
        String id,
        String customer,
        int items,
        long amount,
        String payment,
        String fulfillment,
        String date,
        String channel) {}
