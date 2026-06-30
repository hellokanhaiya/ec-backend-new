package com.ecommerce.auth;

public enum AuthAudience {
    ADMIN,
    CONSUMER;

    public static AuthAudience from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("audience is required");
        }
        return AuthAudience.valueOf(value.trim().toUpperCase());
    }
}
