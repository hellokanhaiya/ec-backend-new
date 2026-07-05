package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.customer.BulkDeleteRequest;
import com.ecommerce.customer.CustomerData;
import com.ecommerce.customer.CustomerExportRow;
import com.ecommerce.customer.CustomerListData;
import com.ecommerce.customer.CustomerOverviewData;
import com.ecommerce.customer.CustomerRequest;
import com.ecommerce.customer.ExportRequest;
import com.ecommerce.customer.ImportRequest;
import com.ecommerce.customer.ImportResultData;
import com.ecommerce.customer.StoreCustomerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CustomerController {
    private final StoreCustomerService customerService;
    private final AccessControlService accessControl;

    public CustomerController(StoreCustomerService customerService, AccessControlService accessControl) {
        this.customerService = customerService;
        this.accessControl = accessControl;
    }

    @GetMapping("/{audience}/auth/customers")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.VIEW);
        CustomerListData data = customerService.list(scope.storeId(), search, status, dateFrom, dateTo, page, size);
        return ok("Customers loaded", data);
    }

    @GetMapping("/{audience}/auth/customers/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.VIEW);
        CustomerOverviewData data = customerService.overview(scope.storeId());
        return ok("Customer overview loaded", data);
    }

    @GetMapping("/{audience}/auth/customers/{publicCustomerId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicCustomerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.VIEW);
        CustomerData data = customerService.get(scope.storeId(), publicCustomerId);
        return ok("Customer loaded", data);
    }

    @PostMapping("/{audience}/auth/customers")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CustomerRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_CREATE, AccessLevel.MANAGE);
        CustomerData data = customerService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Customer created", data);
    }

    @PutMapping("/{audience}/auth/customers/{publicCustomerId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicCustomerId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CustomerRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.EDIT);
        CustomerData data = customerService.update(scope.storeId(), publicCustomerId, request);
        return ok("Customer updated", data);
    }

    @DeleteMapping("/{audience}/auth/customers/{publicCustomerId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicCustomerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.MANAGE);
        customerService.delete(scope.storeId(), publicCustomerId);
        return ok("Customer deleted", null);
    }

    @PostMapping("/{audience}/auth/customers/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody BulkDeleteRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.MANAGE);
        int deleted = customerService.bulkDelete(scope.storeId(), request == null ? null : request.ids());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        return ok("Customers deleted", data);
    }

    @PostMapping("/{audience}/auth/customers/export")
    public ResponseEntity<Map<String, Object>> export(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ExportRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_LIST, AccessLevel.VIEW);
        List<CustomerExportRow> rows = customerService.exportRows(scope.storeId(), request == null ? null : request.ids());
        return ok("Customers exported", rows);
    }

    @PostMapping("/{audience}/auth/customers/import")
    public ResponseEntity<Map<String, Object>> importCustomers(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ImportRequest request) {
        StoreScope scope = resolveScope(authorization, audience, PermissionCatalog.CUSTOMERS_CREATE, AccessLevel.MANAGE);
        ImportResultData result = customerService.importCustomers(
                scope.storeId(),
                scope.ownerPublicUserId(),
                request != null && request.overwrite(),
                request == null ? null : request.rows());
        return ok("Import complete", result);
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
