package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.warehouse.StoreWarehouseService;
import com.ecommerce.warehouse.WarehouseData;
import com.ecommerce.warehouse.WarehouseListData;
import com.ecommerce.warehouse.WarehouseRequest;
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
public class WarehouseController {
    private final StoreWarehouseService warehouseService;
    private final AccessControlService accessControl;

    public WarehouseController(StoreWarehouseService warehouseService, AccessControlService accessControl) {
        this.warehouseService = warehouseService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/warehouses")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.VIEW);
        WarehouseListData data = warehouseService.list(scope.storeId(), scope.ownerPublicUserId(), page, size);
        return ok("Warehouses loaded", data);
    }

    @GetMapping("/{audience}/auth/warehouses/count")
    public ResponseEntity<Map<String, Object>> count(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.VIEW);
        long count = warehouseService.count(scope.storeId(), scope.ownerPublicUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", count);
        return ok("Warehouse count loaded", data);
    }

    @GetMapping("/{audience}/auth/warehouses/{publicWarehouseId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicWarehouseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.VIEW);
        WarehouseData data = warehouseService.get(scope.storeId(), publicWarehouseId);
        return ok("Warehouse loaded", data);
    }

    @PostMapping("/{audience}/auth/warehouses")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody WarehouseRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.MANAGE);
        WarehouseData data = warehouseService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Warehouse created", data);
    }

    @PutMapping("/{audience}/auth/warehouses/{publicWarehouseId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicWarehouseId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody WarehouseRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.EDIT);
        WarehouseData data = warehouseService.update(scope.storeId(), publicWarehouseId, request);
        return ok("Warehouse updated", data);
    }

    @PostMapping("/{audience}/auth/warehouses/{publicWarehouseId}/set-default")
    public ResponseEntity<Map<String, Object>> setDefault(
            @PathVariable String audience,
            @PathVariable String publicWarehouseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.MANAGE);
        WarehouseData data = warehouseService.setDefault(scope.storeId(), publicWarehouseId);
        return ok("Default warehouse updated", data);
    }

    @DeleteMapping("/{audience}/auth/warehouses/{publicWarehouseId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicWarehouseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_WAREHOUSES, AccessLevel.MANAGE);
        warehouseService.delete(scope.storeId(), publicWarehouseId);
        return ok("Warehouse deleted", null);
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
