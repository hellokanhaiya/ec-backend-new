package com.ecommerce.plugin;

import java.util.Set;

/**
 * The tenant scope a validated plugin token operates within. {@code ownerPublicUserId} and
 * {@code countryCode} are resolved from the store profile so plugin reads can call the same
 * storeId-scoped service methods the dashboard uses (several of which take the owner id to seed
 * defaults, or a country code for global rate tables).
 */
public record PluginAccessScope(
        String storeId,
        String orgId,
        String ownerPublicUserId,
        String countryCode,
        Long appId,
        String publicAppId,
        Long tokenId,
        Set<String> scopes) {}
