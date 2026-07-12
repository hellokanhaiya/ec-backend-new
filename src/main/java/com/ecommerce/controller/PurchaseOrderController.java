package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.purchase.PurchaseOrderData;
import com.ecommerce.purchase.PurchaseOrderExportRequest;
import com.ecommerce.purchase.PurchaseOrderListData;
import com.ecommerce.purchase.PurchaseOrderOverviewData;
import com.ecommerce.purchase.PurchaseOrderRequest;
import com.ecommerce.purchase.PurchaseSupplierData;
import com.ecommerce.purchase.PurchaseSupplierListData;
import com.ecommerce.purchase.PurchaseSupplierRequest;
import com.ecommerce.purchase.StorePurchaseOrderService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class PurchaseOrderController {
    private final StorePurchaseOrderService purchaseOrderService;
    private final AccessControlService accessControl;

    public PurchaseOrderController(
            StorePurchaseOrderService purchaseOrderService, AccessControlService accessControl) {
        this.purchaseOrderService = purchaseOrderService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/purchase-orders")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierSearch,
            @RequestParam(required = false) String supplierIds,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.VIEW);
        PurchaseOrderListData data = purchaseOrderService.list(
                scope.storeId(), search, status, supplierSearch, supplierIds, dateFrom, dateTo, page, size);
        return ok("Purchase orders loaded", data);
    }

    @GetMapping("/{audience}/auth/purchase-orders/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.VIEW);
        PurchaseOrderOverviewData data = purchaseOrderService.overview(scope.storeId());
        return ok("Purchase order overview loaded", data);
    }

    @GetMapping("/{audience}/auth/purchase-orders/{publicPurchaseOrderId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicPurchaseOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.VIEW);
        PurchaseOrderData data = purchaseOrderService.get(scope.storeId(), publicPurchaseOrderId);
        return ok("Purchase order loaded", data);
    }

    @PostMapping("/{audience}/auth/purchase-orders")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PurchaseOrderRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.MANAGE);
        PurchaseOrderData data = purchaseOrderService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Purchase order created", data);
    }

    @PutMapping("/{audience}/auth/purchase-orders/{publicPurchaseOrderId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicPurchaseOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PurchaseOrderRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.EDIT);
        PurchaseOrderData data = purchaseOrderService.update(scope.storeId(), publicPurchaseOrderId, request);
        return ok("Purchase order updated", data);
    }

    @PutMapping("/{audience}/auth/purchase-orders/{publicPurchaseOrderId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String audience,
            @PathVariable String publicPurchaseOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.EDIT);
        String status = request == null ? null : request.get("status");
        PurchaseOrderData data = purchaseOrderService.updateStatus(scope.storeId(), publicPurchaseOrderId, status);
        return ok("Purchase order status updated", data);
    }

    @DeleteMapping("/{audience}/auth/purchase-orders/{publicPurchaseOrderId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicPurchaseOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.MANAGE);
        purchaseOrderService.delete(scope.storeId(), publicPurchaseOrderId);
        return ok("Purchase order deleted", null);
    }

    @PostMapping("/{audience}/auth/purchase-orders/export")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PurchaseOrderExportRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.VIEW);
        byte[] data = purchaseOrderService.exportCsv(scope.storeId(), request == null ? null : request.ids());
        return download(data, MediaType.parseMediaType("text/csv"), "purchase-orders-" + Instant.now().toEpochMilli() + ".csv");
    }

    @GetMapping("/{audience}/auth/purchase-orders/suppliers")
    public ResponseEntity<Map<String, Object>> listSuppliers(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.VIEW);
        PurchaseSupplierListData data = purchaseOrderService.listSuppliers(scope.storeId(), search, page, size);
        return ok("Suppliers loaded", data);
    }

    @PostMapping("/{audience}/auth/purchase-orders/suppliers")
    public ResponseEntity<Map<String, Object>> createSupplier(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PurchaseSupplierRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.ORDERS_PURCHASE, AccessLevel.MANAGE);
        PurchaseSupplierData data =
                purchaseOrderService.createSupplier(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Supplier created", data);
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

    private ResponseEntity<byte[]> download(byte[] data, MediaType mediaType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private record StoreScope(String storeId, String ownerPublicUserId) {}
}
