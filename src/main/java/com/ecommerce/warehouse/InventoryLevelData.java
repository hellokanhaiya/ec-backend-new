package com.ecommerce.warehouse;

/** A single product's stock at a single warehouse. */
public record InventoryLevelData(
        String warehousePublicId,
        String warehouseName,
        int onHand,
        int reserved,
        int available,
        int incoming,
        int reorderPoint) {}
