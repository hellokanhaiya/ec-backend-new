package com.ecommerce.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderData(
        String id,
        String purchaseOrderNumber,
        String supplierPublicId,
        String supplierCode,
        String supplierName,
        String supplierEmail,
        String supplierPhone,
        String supplierCompany,
        String warehousePublicId,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        Instant receivedAt,
        String referenceNumber,
        String notes,
        BigDecimal subtotal,
        BigDecimal total,
        int items,
        List<PurchaseOrderLineItemData> products,
        List<String> tags,
        String currencyCode,
        String currencySymbol,
        String currencyCountryCode,
        Instant createdAt,
        Instant updatedAt) {}
