package com.ecommerce.bundle;

import java.util.List;

public record ProductBundleListData(List<ProductBundleSummaryData> items, long total, int page, int size) {}
