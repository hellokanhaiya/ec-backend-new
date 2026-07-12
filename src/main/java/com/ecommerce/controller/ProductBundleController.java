package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.bundle.ProductBundleData;
import com.ecommerce.bundle.ProductBundleListData;
import com.ecommerce.bundle.ProductBundleOverviewData;
import com.ecommerce.bundle.ProductBundleRequest;
import com.ecommerce.bundle.StoreProductBundleService;
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
public class ProductBundleController {
    private final StoreProductBundleService bundleService;
    private final AccessControlService accessControl;

    public ProductBundleController(StoreProductBundleService bundleService, AccessControlService accessControl) {
        this.bundleService = bundleService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/product-bundles")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        ProductBundleListData data = bundleService.list(scope.storeId(), search, type, status, page, size);
        return ok("Bundles loaded", data);
    }

    @GetMapping("/{audience}/auth/product-bundles/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        ProductBundleOverviewData data = bundleService.overview(scope.storeId());
        return ok("Bundle overview loaded", data);
    }

    @GetMapping("/{audience}/auth/product-bundles/{publicBundleId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicBundleId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        ProductBundleData data = bundleService.get(scope.storeId(), publicBundleId);
        return ok("Bundle loaded", data);
    }

    @PostMapping("/{audience}/auth/product-bundles")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductBundleRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_CREATE, AccessLevel.MANAGE);
        ProductBundleData data = bundleService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Bundle created", data);
    }

    @PutMapping("/{audience}/auth/product-bundles/{publicBundleId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicBundleId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProductBundleRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.EDIT);
        ProductBundleData data = bundleService.update(scope.storeId(), publicBundleId, request);
        return ok("Bundle updated", data);
    }

    @DeleteMapping("/{audience}/auth/product-bundles/{publicBundleId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicBundleId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.MANAGE);
        bundleService.delete(scope.storeId(), publicBundleId);
        return ok("Bundle deleted", null);
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
