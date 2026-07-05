package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.tag.StoreTagService;
import com.ecommerce.tag.TagData;
import com.ecommerce.tag.TagRequest;
import java.util.LinkedHashMap;
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
 * Store-scoped tag library — the common/shared tag store. Tags created here (or
 * while editing a customer) are reusable across the store.
 */
@RestController
@RequestMapping("/api/v1")
public class TagController {
    private final StoreTagService tagService;
    private final AccessControlService accessControl;

    public TagController(StoreTagService tagService, AccessControlService accessControl) {
        this.tagService = tagService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/tags")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String storeId = resolveStoreId(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        List<TagData> data = tagService.list(storeId);
        return ok("Tags loaded", data);
    }

    @PostMapping("/{audience}/auth/tags")
    public ResponseEntity<Map<String, Object>> upsert(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TagRequest request) {
        String storeId = resolveStoreId(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.EDIT);
        TagData data = tagService.upsert(storeId, request == null ? null : request.name());
        return ok("Tag saved", data);
    }

    private String resolveStoreId(String authorization, String audience, String permissionKey, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, permissionKey, required);
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
