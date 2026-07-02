package com.ecommerce.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerSummaryData(
        String publicCustomerId,
        String customerCode,
        String firstName,
        String lastName,
        String email,
        String phoneCountryCode,
        String phone,
        String location,
        String status,
        List<String> tags,
        int orders,
        BigDecimal totalSpent,
        BigDecimal wallet,
        Instant createdAt) {}
