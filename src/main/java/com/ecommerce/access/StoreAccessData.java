package com.ecommerce.access;

import java.util.Map;

/**
 * The resolved access of the current user within their store — attached to the account payload
 * (`/v1/store-info`) so the frontend can gate routes and filter the sidebar.
 */
public record StoreAccessData(
        String roleKey,
        String roleName,
        boolean isOwner,
        MemberStatus status,
        Map<String, AccessLevel> permissions) {}
