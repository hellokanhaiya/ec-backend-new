package com.ecommerce.warehouse;

import java.util.List;

/**
 * Paginated inventory rows plus the warehouse columns to render. When a single
 * {@code warehouse} filter is applied, {@code warehouses} still lists all warehouses so
 * the UI can offer the selector; {@code items[].levels} are limited to the filtered one.
 */
public record InventoryListData(
        List<InventoryItemData> items, List<WarehouseData> warehouses, long total, int page, int size) {}
