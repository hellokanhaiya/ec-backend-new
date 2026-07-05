package com.ecommerce.warehouse;

import java.math.BigDecimal;

public record WarehouseRequest(
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String email,
        Boolean defaultWarehouse,
        Boolean fulfillsOnlineOrders,
        Integer priority,
        String status) {}
