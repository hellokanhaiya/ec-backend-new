package com.ecommerce.controller;

import com.ecommerce.access.AccessControlService;
import com.ecommerce.access.AccessLevel;
import com.ecommerce.access.PermissionCatalog;
import com.ecommerce.access.StoreAccessScope;
import com.ecommerce.bulk.BulkJobData;
import com.ecommerce.bulk.ProductBulkService;
import com.ecommerce.bulk.ProductExportRequest;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk product import/export. Each mutating call kicks off an async job and returns
 * a job id immediately; clients poll {@code /products/jobs/{jobId}} for progress and,
 * once complete, download the generated CSV from the returned {@code resultUrl}.
 */
@RestController
@RequestMapping("/api/v1")
public class ProductBulkController {
    private final ProductBulkService bulkService;
    private final AccessControlService accessControl;

    public ProductBulkController(ProductBulkService bulkService, AccessControlService accessControl) {
        this.bulkService = bulkService;
        this.accessControl = accessControl;
    }

    @PostMapping("/{audience}/auth/products/export")
    public ResponseEntity<Map<String, Object>> export(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ProductExportRequest request) {
        StoreAccessScope scope =
                accessControl.requireScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        BulkJobData data =
                bulkService.startExport(scope.storeId(), scope.publicUserId(), scope.orgId(), request);
        return ok("Export started", data);
    }

    @PostMapping("/{audience}/auth/products/import")
    public ResponseEntity<Map<String, Object>> importProducts(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        StoreAccessScope scope = accessControl.requireScope(
                authorization, audience, PermissionCatalog.PRODUCTS_CREATE, AccessLevel.MANAGE);
        BulkJobData data =
                bulkService.startImport(scope.storeId(), scope.publicUserId(), scope.orgId(), file);
        return ok("Import started", data);
    }

    @GetMapping("/{audience}/auth/products/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> job(
            @PathVariable String audience,
            @PathVariable String jobId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreAccessScope scope =
                accessControl.requireScope(authorization, audience, PermissionCatalog.PRODUCTS_LIST, AccessLevel.VIEW);
        BulkJobData data = bulkService.getJob(scope.storeId(), jobId);
        return ok("Job loaded", data);
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }
}
