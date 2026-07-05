package com.ecommerce.access;

import java.util.Map;

/**
 * A default role definition seeded into a new store. {@code locked} roles (Owner) cannot be edited
 * or deleted from the UI; {@code systemRole} marks the five built-in templates.
 */
public record RoleTemplate(
        String roleKey,
        String name,
        String description,
        boolean systemRole,
        boolean locked,
        Map<String, AccessLevel> pages) {}
