package com.ecommerce.product;

import java.util.List;

public record ProductPickerListData(List<ProductPickerData> items, long total, int page, int size, boolean hasMore) {}
