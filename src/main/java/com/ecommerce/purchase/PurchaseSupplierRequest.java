package com.ecommerce.purchase;

public record PurchaseSupplierRequest(
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
        String notes) {}
