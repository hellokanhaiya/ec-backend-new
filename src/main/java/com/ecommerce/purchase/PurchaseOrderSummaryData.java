package com.ecommerce.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseOrderSummaryData(
        String id,
        String purchaseOrderNumber,
        String supplierPublicId,
        String supplierName,
        String supplierEmail,
        LocalDate orderDate,
        LocalDate expectedDate,
        String status,
        BigDecimal total,
        int items,
        String warehousePublicId,
        String referenceNumber,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
