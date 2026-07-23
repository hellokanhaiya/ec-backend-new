package com.ecommerce.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A plugin's manifest.json as served from its {@code appUrl}. Declares the app identity, the API
 * scopes it needs, and the admin extension points it contributes. {@code display} and {@code
 * modal} stay raw JSON — the admin UI owns their interpretation, so the backend only shape-checks
 * them in {@link PluginManifestValidator}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginManifest(
        Integer manifestVersion,
        String id,
        String name,
        String version,
        String description,
        String appUrl,
        List<String> scopes,
        List<Extension> extensions) {

    public static final String EXT_ORDER_DETAIL_ACTION = "order.detail.action";
    public static final String EXT_PRODUCTS_TABLE_COLUMN = "products.table.column";
    public static final String EXT_PLUGIN_PAGE = "plugin.page";
    public static final String EXT_APP_SETTINGS = "app.settings";

    public static final String MODE_MODAL = "modal";
    public static final String MODE_DIRECT = "direct";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extension(
            String type,
            String id,
            String label,
            String icon,
            // order.detail.action
            String mode,
            String url,
            JsonNode modal,
            // products.table.column
            Source source,
            JsonNode display,
            // plugin.page / app.settings
            String path,
            Boolean navigation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(String kind, String resource, String key) {}
}
