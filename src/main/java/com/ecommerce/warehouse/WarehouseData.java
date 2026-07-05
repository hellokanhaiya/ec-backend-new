package com.ecommerce.warehouse;

import java.math.BigDecimal;
import java.time.Instant;

public record WarehouseData(
        String publicWarehouseId,
        String warehouseCode,
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
        boolean defaultWarehouse,
        boolean fulfillsOnlineOrders,
        int priority,
        String status,
        int totalOnHand,
        Instant createdAt,
        Instant updatedAt) {}
