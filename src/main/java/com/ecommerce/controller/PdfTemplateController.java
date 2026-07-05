package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.order.PdfTemplateData;
import com.ecommerce.order.PdfTemplateListData;
import com.ecommerce.order.PdfTemplateRequest;
import com.ecommerce.order.StorePdfTemplateService;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PdfTemplateController {
    private final StorePdfTemplateService templateService;
    private final AccessControlService accessControl;

    public PdfTemplateController(StorePdfTemplateService templateService, AccessControlService accessControl) {
        this.templateService = templateService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/pdf-templates")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String type) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.VIEW);
        PdfTemplateListData data = templateService.list(scope.storeId(), type);
        return ok("PDF templates loaded", data);
    }

    @GetMapping("/{audience}/auth/pdf-templates/{publicTemplateId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicTemplateId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.VIEW);
        PdfTemplateData data = templateService.get(scope.storeId(), publicTemplateId);
        return ok("PDF template loaded", data);
    }

    @PostMapping("/{audience}/auth/pdf-templates")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PdfTemplateRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.MANAGE);
        PdfTemplateData data = templateService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("PDF template created", data);
    }

    @PutMapping("/{audience}/auth/pdf-templates/{publicTemplateId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicTemplateId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PdfTemplateRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.EDIT);
        PdfTemplateData data = templateService.update(scope.storeId(), publicTemplateId, request);
        return ok("PDF template updated", data);
    }

    @DeleteMapping("/{audience}/auth/pdf-templates/{publicTemplateId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicTemplateId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.MANAGE);
        templateService.delete(scope.storeId(), publicTemplateId);
        return ok("PDF template deleted", null);
    }

    @PostMapping("/{audience}/auth/pdf-templates/seed")
    public ResponseEntity<Map<String, Object>> seedDefaults(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_ALL, AccessLevel.MANAGE);
        templateService.seedDefaults(scope.storeId(), scope.ownerPublicUserId());
        return ok("Default PDF templates seeded", null);
    }

    private StoreScope resolveScope(String authorization, String audience, String permissionKey, AccessLevel required) {
        StoreAccessScope scope = accessControl.requireScope(authorization, audience, permissionKey, required);
        return new StoreScope(scope.storeId(), scope.publicUserId());
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    private record StoreScope(String storeId, String ownerPublicUserId) {}
}
