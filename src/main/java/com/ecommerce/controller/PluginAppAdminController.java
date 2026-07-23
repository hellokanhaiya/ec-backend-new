package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.plugin.PluginAppDtos.ActionInvokeRequest;
import com.ecommerce.plugin.PluginAppDtos.AppStatusRequest;
import com.ecommerce.plugin.PluginAppDtos.ContextTokenRequest;
import com.ecommerce.plugin.PluginAppDtos.DevRegisterRequest;
import com.ecommerce.plugin.PluginAppService;
import com.ecommerce.plugin.PluginMetafieldDtos.MetafieldBatchReadRequest;
import com.ecommerce.plugin.PluginMetafieldService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard-side management and consumption of plugin apps: dev registration, manifest refresh,
 * enable/disable, the extensions feed the admin UI renders, batched metafield reads for plugin
 * table columns, context-token minting for iframes, and direct-action invocation. Session-auth
 * only — plugin {@code sk_plg_} tokens authenticate solely on {@code /api/v1/plugin/**}.
 * Management is guarded by the Apps page permission ({@code apps.marketplace}); consumption
 * endpoints need membership plus the permission of the page the extension appears on.
 */
@RestController
@RequestMapping("/api/v1")
public class PluginAppAdminController {
    private final PluginAppService appService;
    private final PluginMetafieldService metafieldService;
    private final AccessControlService accessControl;

    public PluginAppAdminController(
            PluginAppService appService,
            PluginMetafieldService metafieldService,
            AccessControlService accessControl) {
        this.appService = appService;
        this.metafieldService = metafieldService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/plugin-apps")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.APPS_MARKETPLACE, AccessLevel.VIEW);
        return ok("Plugin apps loaded", appService.list(scope.storeId()));
    }

    @PostMapping("/{audience}/auth/plugin-apps/dev-register")
    public ResponseEntity<Map<String, Object>> devRegister(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody DevRegisterRequest request) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.APPS_MARKETPLACE, AccessLevel.MANAGE);
        return ok(
                "Plugin registered. Copy the token and signing secret now — they won't be shown again.",
                appService.devRegister(scope, request));
    }

    @PostMapping("/{audience}/auth/plugin-apps/{publicAppId}/manifest/refresh")
    public ResponseEntity<Map<String, Object>> refreshManifest(
            @PathVariable String audience,
            @PathVariable String publicAppId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.APPS_MARKETPLACE, AccessLevel.MANAGE);
        return ok("Manifest refreshed", appService.refreshManifest(scope.storeId(), publicAppId));
    }

    @PatchMapping("/{audience}/auth/plugin-apps/{publicAppId}")
    public ResponseEntity<Map<String, Object>> setStatus(
            @PathVariable String audience,
            @PathVariable String publicAppId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AppStatusRequest request) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.APPS_MARKETPLACE, AccessLevel.MANAGE);
        return ok("App updated", appService.setStatus(
                scope.storeId(), publicAppId, request == null ? null : request.status()));
    }

    // Consumption endpoints: any store member may read the feed (it's UI metadata); the
    // endpoints that move data are gated by the permission of the page they serve.

    @GetMapping("/{audience}/auth/plugin-apps/extensions")
    public ResponseEntity<Map<String, Object>> extensions(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireMember(authorization, audience);
        return ok("Plugin extensions loaded", appService.extensionsFeed(scope.storeId()));
    }

    @PostMapping("/{audience}/auth/plugin-apps/metafields/batch")
    public ResponseEntity<Map<String, Object>> metafieldsBatch(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MetafieldBatchReadRequest request) {
        // v1 plugin columns exist only on the products table.
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        return ok("Metafields loaded", metafieldService.batchRead(scope.storeId(), request));
    }

    @PostMapping("/{audience}/auth/plugin-apps/{publicAppId}/context-token")
    public ResponseEntity<Map<String, Object>> contextToken(
            @PathVariable String audience,
            @PathVariable String publicAppId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ContextTokenRequest request) {
        // The token carries only ids/context — the plugin still needs its own sk_plg_ token
        // (with its granted scopes) to read any actual data.
        StoreAccessScope scope = accessControl.requireMember(authorization, audience);
        return ok("Context token minted", appService.mintContextToken(
                scope.storeId(), publicAppId, request, scope.publicUserId()));
    }

    @PostMapping("/{audience}/auth/plugin-apps/{publicAppId}/actions/{extensionId}/invoke")
    public ResponseEntity<byte[]> invokeAction(
            @PathVariable String audience,
            @PathVariable String publicAppId,
            @PathVariable String extensionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ActionInvokeRequest request) {
        // v1 direct actions live only on the order detail page.
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.VIEW);
        return appService.invokeAction(
                scope.storeId(), publicAppId, extensionId, request, scope.publicUserId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
