package com.ecommerce.auth;

public enum AuthPurpose {
    SIGNIN,
    SIGNUP;

    public static AuthPurpose from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
        return AuthPurpose.valueOf(value.trim().toUpperCase());
    }
}
