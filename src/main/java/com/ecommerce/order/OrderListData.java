package com.ecommerce.order;

import java.util.List;

public record OrderListData(List<OrderSummaryData> items, long total, int page, int size) {}
