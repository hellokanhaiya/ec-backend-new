package com.ecommerce.plugin;

import com.ecommerce.plugin.PluginMetafieldDtos.MetafieldBatchReadRequest;
import com.ecommerce.plugin.PluginMetafieldDtos.MetafieldData;
import com.ecommerce.plugin.PluginMetafieldDtos.MetafieldEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads and writes plugin metafields. Writes always land in the calling app's own namespace
 * (its public app id); the dashboard-side batch read spans namespaces because the admin UI
 * renders columns contributed by many apps at once.
 */
@Service
public class PluginMetafieldService {
    static final int MAX_BATCH_ENTRIES = 200;
    static final int MAX_VALUE_LENGTH = 8_192;

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,119}$");
    private static final Set<String> RESOURCE_TYPES = Set.of(
            PluginMetafield.RESOURCE_PRODUCT, PluginMetafield.RESOURCE_ORDER, PluginMetafield.RESOURCE_APP);

    private final PluginMetafieldRepository repository;
    private final ObjectMapper objectMapper;

    public PluginMetafieldService(PluginMetafieldRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MetafieldData> list(PluginAccessScope scope, String resourceType, String resourceId) {
        String type = requireResourceType(resourceType);
        List<MetafieldData> data = new ArrayList<>();
        for (PluginMetafield field : repository.findByStoreIdAndNamespaceAndResourceTypeAndResourceId(
                scope.storeId(), scope.publicAppId(), type, requireResourceId(resourceId))) {
            data.add(toData(field));
        }
        return data;
    }

    @Transactional
    public List<MetafieldData> set(PluginAccessScope scope, List<MetafieldEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one metafield entry is required");
        }
        if (entries.size() > MAX_BATCH_ENTRIES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At most " + MAX_BATCH_ENTRIES + " entries per request");
        }
        List<MetafieldData> result = new ArrayList<>();
        for (MetafieldEntry entry : entries) {
            result.add(setOne(scope, entry));
        }
        return result;
    }

    /** Dashboard-side read for table columns: one query per page of rows, across all apps. */
    @Transactional(readOnly = true)
    public List<MetafieldData> batchRead(String storeId, MetafieldBatchReadRequest request) {
        if (request == null || request.resourceIds() == null || request.resourceIds().isEmpty()
                || request.keys() == null || request.keys().isEmpty()) {
            return List.of();
        }
        String type = requireResourceType(request.resourceType());
        Set<String> ids = new LinkedHashSet<>(request.resourceIds());
        Set<String> keys = new LinkedHashSet<>(request.keys());
        if (ids.size() > MAX_BATCH_ENTRIES || keys.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch read is too large");
        }
        List<MetafieldData> data = new ArrayList<>();
        for (PluginMetafield field :
                repository.findByStoreIdAndResourceTypeAndResourceIdInAndMkeyIn(storeId, type, ids, keys)) {
            data.add(toData(field));
        }
        return data;
    }

    private MetafieldData setOne(PluginAccessScope scope, MetafieldEntry entry) {
        String type = requireResourceType(entry.resourceType());
        String resourceId = requireResourceId(entry.resourceId());
        String key = requireKey(entry.key());

        Optional<PluginMetafield> existing =
                repository.findByStoreIdAndNamespaceAndResourceTypeAndResourceIdAndMkey(
                        scope.storeId(), scope.publicAppId(), type, resourceId, key);

        JsonNode value = entry.value();
        if (value == null || value.isNull()) {
            existing.ifPresent(repository::delete);
            return new MetafieldData(scope.publicAppId(), type, resourceId, key, null, null);
        }

        String json = writeValue(value);
        PluginMetafield field = existing.orElseGet(() -> {
            PluginMetafield created = new PluginMetafield();
            created.setStoreId(scope.storeId());
            created.setAppId(scope.appId());
            created.setNamespace(scope.publicAppId());
            created.setResourceType(type);
            created.setResourceId(resourceId);
            created.setMkey(key);
            return created;
        });
        field.setValueJson(json);
        return toData(repository.save(field));
    }

    private String requireResourceType(String resourceType) {
        String type = resourceType == null ? "" : resourceType.trim().toLowerCase();
        if (!RESOURCE_TYPES.contains(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "resourceType must be one of: " + String.join(", ", RESOURCE_TYPES));
        }
        return type;
    }

    private String requireResourceId(String resourceId) {
        if (resourceId == null || resourceId.isBlank() || resourceId.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceId is required (max 64 chars)");
        }
        return resourceId.trim();
    }

    private String requireKey(String key) {
        String value = key == null ? "" : key.trim();
        if (!KEY_PATTERN.matcher(value).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Metafield key must be lowercase letters/digits/._- and at most 120 chars: " + value);
        }
        return value;
    }

    private String writeValue(JsonNode value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() > MAX_VALUE_LENGTH) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Metafield value is too large (max " + MAX_VALUE_LENGTH + " chars)");
            }
            return json;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Metafield value is not valid JSON");
        }
    }

    private MetafieldData toData(PluginMetafield field) {
        JsonNode value = null;
        if (field.getValueJson() != null) {
            try {
                value = objectMapper.readTree(field.getValueJson());
            } catch (Exception ignored) {
                // Unreadable stored value degrades to null rather than failing the whole read.
            }
        }
        return new MetafieldData(
                field.getNamespace(),
                field.getResourceType(),
                field.getResourceId(),
                field.getMkey(),
                value,
                field.getUpdatedAt());
    }
}
