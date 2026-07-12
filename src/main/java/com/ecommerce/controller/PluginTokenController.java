package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.plugin.PluginScopeCatalog;
import com.ecommerce.plugin.PluginTokenDtos.TokenCreateRequest;
import com.ecommerce.plugin.PluginTokenDtos.TokenCreatedData;
import com.ecommerce.plugin.PluginTokenDtos.TokenSummaryData;
import com.ecommerce.plugin.PluginTokenService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manages plugin API tokens from the dashboard (Settings → API & Developers). Guarded by the
 * {@code settings.api} permission — this is the only place plugin tokens are minted or revoked;
 * the tokens themselves authenticate solely on {@code /api/v1/plugin/**}.
 */
@RestController
@RequestMapping("/api/v1")
public class PluginTokenController {
    private final PluginTokenService tokenService;
    private final AccessControlService accessControl;

    public PluginTokenController(PluginTokenService tokenService, AccessControlService accessControl) {
        this.tokenService = tokenService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/settings/api-tokens")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.SETTINGS_API, AccessLevel.VIEW);
        List<TokenSummaryData> data = tokenService.list(scope.storeId());
        return ok("API tokens loaded", data);
    }

    @GetMapping("/{audience}/auth/settings/api-tokens/scopes")
    public ResponseEntity<Map<String, Object>> scopes(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        accessControl.requireScope(authorization, audience, PermissionCatalog.SETTINGS_API, AccessLevel.VIEW);
        return ok("API token scopes loaded", PluginScopeCatalog.rules());
    }

    @PostMapping("/{audience}/auth/settings/api-tokens")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TokenCreateRequest request) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.SETTINGS_API, AccessLevel.MANAGE);
        TokenCreatedData data = tokenService.create(scope, request);
        return ok("API token created. Copy it now — it won't be shown again.", data);
    }

    @DeleteMapping("/{audience}/auth/settings/api-tokens/{publicTokenId}")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable String audience,
            @PathVariable String publicTokenId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.SETTINGS_API, AccessLevel.MANAGE);
        TokenSummaryData data = tokenService.revoke(scope.storeId(), publicTokenId);
        return ok("API token revoked", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
