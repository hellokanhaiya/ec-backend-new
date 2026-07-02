package com.ecommerce.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerData(
        String publicCustomerId,
        String customerCode,
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone,
        boolean acceptsEmail,
        boolean acceptsSms,
        boolean acceptsWhatsapp,
        boolean acceptsPromos,
        String status,
        BigDecimal wallet,
        Integer rewardPoints,
        BigDecimal storeCredit,
        String notes,
        List<String> tags,
        List<CustomerAddressData> addresses,
        Instant createdAt,
        Instant updatedAt) {}
