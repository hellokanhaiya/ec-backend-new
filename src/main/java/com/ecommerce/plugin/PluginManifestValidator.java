package com.ecommerce.plugin;

import com.ecommerce.plugin.PluginManifest.Extension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Structural validation for plugin manifests; every failure is a 400 with a pointed message. */
@Component
public class PluginManifestValidator {
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,63}$");
    private static final Set<String> EXTENSION_TYPES = Set.of(
            PluginManifest.EXT_ORDER_DETAIL_ACTION,
            PluginManifest.EXT_PRODUCTS_TABLE_COLUMN,
            PluginManifest.EXT_PLUGIN_PAGE,
            PluginManifest.EXT_APP_SETTINGS);
    private static final Set<String> ACTION_MODES = Set.of(PluginManifest.MODE_MODAL, PluginManifest.MODE_DIRECT);
    private static final Set<String> COLUMN_SOURCE_KINDS = Set.of("metafield");
    private static final Set<String> COLUMN_RESOURCES = Set.of("product");

    public void validate(PluginManifest manifest) {
        if (manifest == null) {
            throw bad("Manifest body is required");
        }
        if (manifest.manifestVersion() == null || manifest.manifestVersion() != 1) {
            throw bad("manifestVersion must be 1");
        }
        if (manifest.id() == null || !SLUG_PATTERN.matcher(manifest.id()).matches()) {
            throw bad("id must be a kebab-case slug (lowercase letters, digits, hyphens)");
        }
        requireText(manifest.name(), 120, "name");
        requireText(manifest.version(), 40, "version");
        if (manifest.appUrl() == null || !isHttpUrl(manifest.appUrl())) {
            throw bad("appUrl must be an absolute http(s) URL");
        }
        if (manifest.scopes() == null || manifest.scopes().isEmpty()) {
            throw bad("At least one scope is required");
        }
        for (String scope : manifest.scopes()) {
            if (!PluginScopeCatalog.isValid(scope)) {
                throw bad("Unknown scope: " + scope);
            }
        }
        validateExtensions(manifest.extensions());
    }

    private void validateExtensions(List<Extension> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return; // A pure-API app with no admin UI is fine.
        }
        if (extensions.size() > 20) {
            throw bad("At most 20 extensions per manifest");
        }
        Set<String> seen = new HashSet<>();
        for (Extension extension : extensions) {
            if (extension.type() == null || !EXTENSION_TYPES.contains(extension.type())) {
                throw bad("extension.type must be one of: " + String.join(", ", EXTENSION_TYPES));
            }
            if (extension.id() == null || !SLUG_PATTERN.matcher(extension.id()).matches()) {
                throw bad("extension.id must be a kebab-case slug (" + extension.type() + ")");
            }
            if (!seen.add(extension.type() + ":" + extension.id())) {
                throw bad("Duplicate extension id: " + extension.id());
            }
            requireText(extension.label(), 60, "extension.label (" + extension.id() + ")");
            switch (extension.type()) {
                case PluginManifest.EXT_ORDER_DETAIL_ACTION -> validateAction(extension);
                case PluginManifest.EXT_PRODUCTS_TABLE_COLUMN -> validateColumn(extension);
                case PluginManifest.EXT_PLUGIN_PAGE, PluginManifest.EXT_APP_SETTINGS -> validatePage(extension);
                default -> throw bad("Unsupported extension type: " + extension.type());
            }
        }
    }

    private void validateAction(Extension extension) {
        String mode = extension.mode() == null ? PluginManifest.MODE_MODAL : extension.mode();
        if (!ACTION_MODES.contains(mode)) {
            throw bad("action mode must be one of: " + String.join(", ", ACTION_MODES));
        }
        requirePath(extension.url(), "extension.url (" + extension.id() + ")");
    }

    private void validateColumn(Extension extension) {
        PluginManifest.Source source = extension.source();
        if (source == null || source.kind() == null || !COLUMN_SOURCE_KINDS.contains(source.kind())) {
            throw bad("column source.kind must be one of: " + String.join(", ", COLUMN_SOURCE_KINDS));
        }
        if (source.resource() == null || !COLUMN_RESOURCES.contains(source.resource())) {
            throw bad("column source.resource must be one of: " + String.join(", ", COLUMN_RESOURCES));
        }
        requireText(source.key(), 120, "column source.key (" + extension.id() + ")");
        if (extension.display() == null || !extension.display().isObject()
                || extension.display().path("kind").asText("").isBlank()) {
            throw bad("column display must be an object with a kind (badge, text, number)");
        }
    }

    private void validatePage(Extension extension) {
        requirePath(extension.path(), "extension.path (" + extension.id() + ")");
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw bad(field + " is required (max " + maxLength + " chars)");
        }
    }

    private void requirePath(String value, String field) {
        if (value == null || !value.startsWith("/") || value.length() > 300) {
            throw bad(field + " must be a path starting with /");
        }
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
