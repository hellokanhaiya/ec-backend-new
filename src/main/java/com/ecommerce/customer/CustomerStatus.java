package com.ecommerce.customer;

public enum CustomerStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED;

    public static CustomerStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return CustomerStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
