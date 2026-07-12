package com.ecommerce.vendor;

import java.math.BigDecimal;

public record VendorRequest(
        String name,
        String company,
        String email,
        String phone,
        String logoUrl,
        String status,
        String commissionType,
        BigDecimal commissionRate,
        String payoutAccountName,
        String payoutAccountNumber,
        String payoutIfsc,
        String payoutUpi,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,
        String notes) {}
