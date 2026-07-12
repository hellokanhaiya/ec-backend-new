package com.ecommerce.billing;

/** What the store's current plan is allowed to do — consumed by the UI to gate features. */
public record EntitlementsData(
        int tier,
        String planCode,
        String planName,
        boolean bundles,
        boolean purchaseOrders) {}
