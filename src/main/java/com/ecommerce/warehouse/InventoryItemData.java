package com.ecommerce.warehouse;

import java.math.BigDecimal;
import java.util.List;

/** A product row on the inventory page, with its stock broken down per warehouse. */
public record InventoryItemData(
        String productPublicId,
        String name,
        String sku,
        String image,
        boolean trackInventory,
        BigDecimal price,
        int totalOnHand,
        int totalAvailable,
        List<InventoryLevelData> levels) {}
