package com.ecommerce.warehouse;

/**
 * Adjust stock for one product at one warehouse. {@code mode} is {@code SET} (replace
 * on-hand with {@code quantity}) or {@code DELTA} (add {@code quantity}, may be negative).
 * {@code reorderPoint} is optional and updated only when provided.
 */
public record InventoryAdjustRequest(
        String warehousePublicId,
        String productPublicId,
        String mode,
        Integer quantity,
        Integer reorderPoint,
        String reason) {}
