package com.ecommerce.customer;

public record CustomerAddressRequest(
        String type,
        Boolean isDefault,
        String country,
        String firstName,
        String lastName,
        String company,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String phoneCountryCode,
        String phone) {}
