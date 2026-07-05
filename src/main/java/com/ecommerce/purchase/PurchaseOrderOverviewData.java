package com.ecommerce.purchase;

import java.math.BigDecimal;

public record PurchaseOrderOverviewData(
        long total,
        long draft,
        long ordered,
        long received,
        long cancelled,
        long pendingReceipt,
        BigDecimal totalValue) {}
