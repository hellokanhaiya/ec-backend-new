package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.warehouse.InventoryAdjustRequest;
import com.ecommerce.warehouse.InventoryItemData;
import com.ecommerce.warehouse.InventoryListData;
import com.ecommerce.warehouse.InventoryTransferRequest;
import com.ecommerce.warehouse.StoreInventoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
    private final StoreInventoryService inventoryService;
    private final AccessControlService accessControl;

    public InventoryController(StoreInventoryService inventoryService, AccessControlService accessControl) {
        this.inventoryService = inventoryService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/inventory")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String warehouse,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size,
            @RequestParam(required = false) String skus) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_INVENTORY, AccessLevel.VIEW);
        InventoryListData data =
                inventoryService.list(scope.storeId(), scope.ownerPublicUserId(), search, warehouse, page, size, skus);
        return ok("Inventory loaded", data);
    }

    @PostMapping("/{audience}/auth/inventory/adjust")
    public ResponseEntity<Map<String, Object>> adjust(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody InventoryAdjustRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_INVENTORY, AccessLevel.EDIT);
        InventoryItemData data = inventoryService.adjust(scope.storeId(), request);
        return ok("Inventory adjusted", data);
    }

    @PostMapping("/{audience}/auth/inventory/transfer")
    public ResponseEntity<Map<String, Object>> transfer(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody InventoryTransferRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.PRODUCTS_INVENTORY, AccessLevel.EDIT);
        inventoryService.transfer(scope.storeId(), request);
        return ok("Stock transferred", null);
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
