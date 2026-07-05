package com.ecommerce.purchase;

import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderRequest(
        String supplierPublicId,
        PurchaseSupplierRequest supplier,
        String warehousePublicId,
        String status,
        LocalDate orderDate,
        LocalDate expectedDate,
        String referenceNumber,
        String notes,
        List<String> tags,
        List<PurchaseOrderLineItemRequest> products) {}
