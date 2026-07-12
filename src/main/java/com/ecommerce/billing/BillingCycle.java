package com.ecommerce.billing;

import java.time.LocalDate;
import java.util.Locale;

public enum BillingCycle {
    MONTHLY,
    YEARLY;

    public static BillingCycle from(String value) {
        if (value == null || value.isBlank()) {
            return MONTHLY;
        }
        return BillingCycle.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Extend an expiry/start date by one billing period. */
    public LocalDate advance(LocalDate from) {
        return this == YEARLY ? from.plusYears(1) : from.plusMonths(1);
    }
}
