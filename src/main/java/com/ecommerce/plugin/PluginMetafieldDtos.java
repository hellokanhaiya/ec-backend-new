package com.ecommerce.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Request/response payloads for plugin metafields. */
public final class PluginMetafieldDtos {
    private PluginMetafieldDtos() {}

    /** One write; a {@code null}/JSON-null value deletes the entry. */
    public record MetafieldEntry(String resourceType, String resourceId, String key, JsonNode value) {}

    public record MetafieldData(
            String namespace,
            String resourceType,
            String resourceId,
            String key,
            JsonNode value,
            Instant updatedAt) {}

    public record MetafieldSetRequest(List<MetafieldEntry> entries) {}

    /** Dashboard-side batched read across all app namespaces for the visible table page. */
    public record MetafieldBatchReadRequest(String resourceType, List<String> resourceIds, List<String> keys) {}
}
