package com.ecommerce.warehouse;

import java.util.List;

public record WarehouseListData(List<WarehouseData> items, long total, int page, int size) {}
