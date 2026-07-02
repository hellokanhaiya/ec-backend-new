package com.ecommerce.customer;

import java.math.BigDecimal;
import java.util.List;

public record CustomerRequest(
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone,
        Boolean acceptsEmail,
        Boolean acceptsSms,
        Boolean acceptsWhatsapp,
        Boolean acceptsPromos,
        String status,
        BigDecimal wallet,
        Integer rewardPoints,
        BigDecimal storeCredit,
        String notes,
        List<String> tags,
        List<CustomerAddressRequest> addresses) {}
