package com.ecommerce.order;

public record OrderAddressRequest(
        String line1,
        String line2,
        String city,
        String state,
        String pincode,
        String country) {}
