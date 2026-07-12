package com.ecommerce.controller;

import com.ecommerce.customer.StoreCustomerService;
import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginApiResponses;
import com.ecommerce.plugin.PluginExportRequest;
import com.ecommerce.plugin.PluginScopeCatalog;
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

/** Read-only plugin API for customers. Plugin-token auth only; no write handlers. */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginCustomerApiController {
    private final StoreCustomerService customerService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginCustomerApiController(
            StoreCustomerService customerService, PluginAccessControlService pluginAccessControl) {
        this.customerService = customerService;
        this.pluginAccessControl = pluginAccessControl;
    }

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> listCustomers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CUSTOMERS_READ);
        var data = customerService.list(scope.storeId(), search, status, dateFrom, dateTo, page, size);
        return PluginApiResponses.ok("Customers loaded", data);
    }

    @GetMapping("/customers/overview")
    public ResponseEntity<Map<String, Object>> customersOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CUSTOMERS_READ);
        return PluginApiResponses.ok("Customers overview loaded", customerService.overview(scope.storeId()));
    }

    @GetMapping("/customers/{publicCustomerId}")
    public ResponseEntity<Map<String, Object>> getCustomer(
            @PathVariable String publicCustomerId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CUSTOMERS_READ);
        return PluginApiResponses.ok("Customer loaded", customerService.get(scope.storeId(), publicCustomerId));
    }

    @PostMapping("/customers/export")
    public ResponseEntity<Map<String, Object>> exportCustomers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PluginExportRequest request) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CUSTOMERS_EXPORT);
        var rows = customerService.exportRows(scope.storeId(), request == null ? null : request.ids());
        return PluginApiResponses.ok("Customers exported", rows);
    }
}
