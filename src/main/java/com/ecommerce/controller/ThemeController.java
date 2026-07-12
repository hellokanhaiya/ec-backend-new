package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.theme.ThemeDtos.CreateThemeRequest;
import com.ecommerce.theme.ThemeDtos.PublishRequest;
import com.ecommerce.theme.ThemeDtos.SaveDraftRequest;
import com.ecommerce.theme.ThemeDtos.ThemeData;
import com.ecommerce.theme.ThemeDtos.ThemeVersionData;
import com.ecommerce.theme.ThemeService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront theme builder API. Every theme (its pages, sections and settings)
 * is persisted as JSON and versioned on publish, so the builder can save drafts,
 * publish, list history and roll back — all scoped to the caller's store.
 */
@RestController
@RequestMapping("/api/v1/{audience}/auth/themes")
public class ThemeController {
    private final ThemeService themeService;
    private final AccessControlService accessControl;

    public ThemeController(ThemeService themeService, AccessControlService accessControl) {
        this.themeService = themeService;
        this.accessControl = accessControl;
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.VIEW);
        return ok("Themes loaded", themeService.list(storeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.VIEW);
        return ok("Theme loaded", themeService.get(storeId, id));
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateThemeRequest request) {
        String storeId = scope(authorization, audience, AccessLevel.EDIT);
        return ok("Theme created", themeService.create(storeId, request));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Map<String, Object>> duplicate(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.EDIT);
        return ok("Theme duplicated", themeService.duplicate(storeId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.MANAGE);
        themeService.delete(storeId, id);
        return ok("Theme deleted", null);
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<Map<String, Object>> saveDraft(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SaveDraftRequest request) {
        String storeId = scope(authorization, audience, AccessLevel.EDIT);
        return ok("Draft saved", themeService.saveDraft(storeId, id, request.draft()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Map<String, Object>> publish(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PublishRequest request) {
        String storeId = scope(authorization, audience, AccessLevel.MANAGE);
        return ok("Theme published", themeService.publish(storeId, id, request.draft(), request.label()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activate(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.MANAGE);
        return ok("Theme activated", themeService.activate(storeId, id));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<Map<String, Object>> versions(
            @PathVariable String audience,
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.VIEW);
        List<ThemeVersionData> data = themeService.listVersions(storeId, id);
        return ok("Versions loaded", data);
    }

    @PostMapping("/{id}/rollback/{versionId}")
    public ResponseEntity<Map<String, Object>> rollback(
            @PathVariable String audience,
            @PathVariable String id,
            @PathVariable String versionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = scope(authorization, audience, AccessLevel.EDIT);
        ThemeData data = themeService.rollback(storeId, id, versionId);
        return ok("Rolled back to version", data);
    }

    private String scope(String authorization, String audience, AccessLevel required) {
        StoreAccessScope scope =
                accessControl.requireScope(authorization, audience, PermissionCatalog.STOREFRONT_THEMES, required);
        return scope.storeId();
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
