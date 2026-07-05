package com.ecommerce.warehouse;

/** Move {@code quantity} units of a product from one warehouse to another. */
public record InventoryTransferRequest(
        String fromWarehousePublicId, String toWarehousePublicId, String productPublicId, Integer quantity) {}
