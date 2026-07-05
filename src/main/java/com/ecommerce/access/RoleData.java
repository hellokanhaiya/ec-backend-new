package com.ecommerce.access;

import java.util.Map;

/** A role plus its full page-access matrix, for the Users &amp; Roles UI. */
public record RoleData(
        String publicId,
        String roleKey,
        String name,
        String description,
        boolean systemRole,
        boolean locked,
        int memberCount,
        Map<String, AccessLevel> pages) {}
