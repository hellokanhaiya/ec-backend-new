package com.ecommerce.plugin;

import java.time.Instant;
import java.util.List;

/** Request/response payloads for plugin API token management. */
public final class PluginTokenDtos {
    private PluginTokenDtos() {}

    /** @param expiresInDays null or {@code <= 0} means the token never expires. */
    public record TokenCreateRequest(String name, List<String> scopes, Integer expiresInDays) {}

    /** Returned exactly once at creation — the only time {@code token} plaintext leaves the server. */
    public record TokenCreatedData(
            String publicTokenId,
            String name,
            String token,
            String maskedToken,
            List<String> scopes,
            Instant expiresAt,
            Instant createdAt) {}

    public record TokenSummaryData(
            String publicTokenId,
            String name,
            String maskedToken,
            List<String> scopes,
            Instant expiresAt,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt) {}
}
