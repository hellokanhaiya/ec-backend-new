package com.ecommerce.access;

/**
 * The tenant scope for a permitted request: the store to operate on plus the caller's identity.
 * Returned by {@link AccessControlService#requireScope} after a permission check passes.
 */
public record StoreAccessScope(
        String storeId,
        String orgId,
        String publicUserId,
        Long userId,
        boolean owner) {}
