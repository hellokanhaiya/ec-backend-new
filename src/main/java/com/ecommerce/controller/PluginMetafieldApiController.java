package com.ecommerce.controller;

import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginApiResponses;
import com.ecommerce.plugin.PluginMetafieldDtos.MetafieldSetRequest;
import com.ecommerce.plugin.PluginMetafieldService;
import com.ecommerce.plugin.PluginScopeCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plugin API for metafields — the only plugin-writable storage. Authenticates via {@code
 * sk_plg_} tokens; every read and write is confined to the calling app's own namespace, so this
 * surface can never touch platform data or another app's data.
 */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginMetafieldApiController {
    private final PluginMetafieldService metafieldService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginMetafieldApiController(
            PluginMetafieldService metafieldService, PluginAccessControlService pluginAccessControl) {
        this.metafieldService = metafieldService;
        this.pluginAccessControl = pluginAccessControl;
    }

    @GetMapping("/metafields/{resourceType}/{resourceId}")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String resourceType,
            @PathVariable String resourceId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.METAFIELDS_READ);
        return PluginApiResponses.ok(
                "Metafields loaded", metafieldService.list(scope, resourceType, resourceId));
    }

    @PostMapping("/metafields/batch-set")
    public ResponseEntity<Map<String, Object>> batchSet(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MetafieldSetRequest request) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.METAFIELDS_WRITE);
        List<?> data = metafieldService.set(scope, request == null ? null : request.entries());
        return PluginApiResponses.ok("Metafields saved", data);
    }
}
