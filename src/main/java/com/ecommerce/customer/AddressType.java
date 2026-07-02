package com.ecommerce.customer;

public enum AddressType {
    SHIPPING,
    BILLING,
    OTHER;

    public static AddressType from(String value) {
        if (value == null || value.isBlank()) {
            return SHIPPING;
        }
        try {
            return AddressType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SHIPPING;
        }
    }
}
