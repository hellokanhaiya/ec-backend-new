package com.ecommerce.purchase;

import java.util.List;

public record PurchaseOrderListData(List<PurchaseOrderSummaryData> items, long total, int page, int size) {}
