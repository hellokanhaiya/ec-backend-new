package com.ecommerce.product;

import java.util.List;

public record ProductListData(List<ProductSummaryData> items, long total, int page, int size) {}
