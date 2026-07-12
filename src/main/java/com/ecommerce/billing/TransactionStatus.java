package com.ecommerce.billing;

import java.util.Locale;

public enum TransactionStatus {
    CREATED,
    PAID,
    FAILED;

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
