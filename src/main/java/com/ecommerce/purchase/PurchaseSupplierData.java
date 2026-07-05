package com.ecommerce.purchase;

import java.time.Instant;

public record PurchaseSupplierData(
        String publicSupplierId,
        String supplierCode,
        String name,
        String email,
        String phone,
        String company,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String pincode,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
