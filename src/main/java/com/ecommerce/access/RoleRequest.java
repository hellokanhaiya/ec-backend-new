package com.ecommerce.access;

import java.util.Map;

/** Create/update payload for a role. {@code pages} is a partial or full permission matrix. */
public record RoleRequest(
        String name,
        String description,
        Map<String, AccessLevel> pages) {}
