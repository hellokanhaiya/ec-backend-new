package com.ecommerce.auth;

public enum AuthChannel {
    EMAIL,
    PHONE,
    WHATSAPP;

    public static AuthChannel from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        return AuthChannel.valueOf(value.trim().toUpperCase());
    }
}
