package com.ecommerce.billing;

import java.util.Locale;

public enum SubscriptionStatus {
    NONE,
    ACTIVE,
    EXPIRED,
    CANCELLED;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
