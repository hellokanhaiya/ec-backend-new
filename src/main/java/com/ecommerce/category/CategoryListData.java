package com.ecommerce.category;

import java.util.List;

public record CategoryListData(List<CategorySummaryData> items, long total) {}
