package com.ecommerce.plugin;

import com.ecommerce.plugin.PluginTokenDtos.TokenCreatedData;
import java.time.Instant;
import java.util.List;

/** Request/response payloads for plugin app (manifest/extension) management. */
public final class PluginAppDtos {
    private PluginAppDtos() {}

    public record DevRegisterRequest(String name, String manifestUrl) {}

    public record AppStatusRequest(String status) {}

    public record AppSummaryData(
            String publicAppId,
            String name,
            String description,
            String status,
            boolean devMode,
            String appUrl,
            String manifestUrl,
            String manifestVersion,
            List<String> scopes,
            int extensionCount,
            Instant createdAt,
            Instant updatedAt) {}

    /** Returned exactly once — the only time the token plaintext and signing secret leave the server. */
    public record DevRegisteredData(AppSummaryData app, TokenCreatedData token, String signingSecret) {}

    /** One extension the admin UI should render, with just enough app identity to act on it. */
    public record ExtensionFeedItem(
            String appId, String appName, String appUrl, boolean devMode, PluginManifest.Extension extension) {}

    public record ContextTokenRequest(String surface, String resourceType, String resourceId) {}

    public record ContextTokenData(String token, Instant expiresAt) {}

    public record ActionInvokeRequest(String resourceType, String resourceId) {}
}
