package com.ecommerce.order;

public record InlineCustomerRequest(
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone) {}
