package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.vendor.VendorData;
import com.ecommerce.vendor.VendorListData;
import com.ecommerce.vendor.VendorOverviewData;
import com.ecommerce.vendor.VendorRequest;
import com.ecommerce.vendor.VendorService;
import com.ecommerce.vendor.VendorStatus;
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
public class VendorController {

    private final VendorService service;
    private final AccessControlService accessControl;

    public VendorController(VendorService service, AccessControlService accessControl) {
        this.service = service;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/vendors")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        VendorListData data =
                service.list(scope.storeId(), scope.ownerPublicUserId(), search, status, page, size);
        return ok("Vendors loaded", data);
    }

    @GetMapping("/{audience}/auth/vendors/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        VendorOverviewData data = service.overview(scope.storeId(), scope.ownerPublicUserId());
        return ok("Vendor overview loaded", data);
    }

    @GetMapping("/{audience}/auth/vendors/{publicVendorId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicVendorId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.VIEW);
        VendorData data = service.get(scope.storeId(), publicVendorId);
        return ok("Vendor loaded", data);
    }

    @PostMapping("/{audience}/auth/vendors")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody VendorRequest request) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.MANAGE);
        VendorData data = service.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Vendor created", data);
    }

    @PutMapping("/{audience}/auth/vendors/{publicVendorId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicVendorId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody VendorRequest request) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        VendorData data = service.update(scope.storeId(), publicVendorId, request);
        return ok("Vendor updated", data);
    }

    @PostMapping("/{audience}/auth/vendors/{publicVendorId}/status")
    public ResponseEntity<Map<String, Object>> setStatus(
            @PathVariable String audience,
            @PathVariable String publicVendorId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String value) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.EDIT);
        VendorData data = service.setStatus(scope.storeId(), publicVendorId, VendorStatus.from(value));
        return ok("Vendor status updated", data);
    }

    @DeleteMapping("/{audience}/auth/vendors/{publicVendorId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicVendorId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, AccessLevel.MANAGE);
        service.delete(scope.storeId(), publicVendorId);
        return ok("Vendor deleted", null);
    }

    // --- Helpers ------------------------------------------------------------

    private StoreScope resolveScope(String authorization, String audience, AccessLevel required) {
        StoreAccessScope scope =
                accessControl.requireScope(
                        authorization, audience, PermissionCatalog.MARKETPLACE_VENDORS, required);
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
