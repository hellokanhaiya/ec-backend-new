package com.ecommerce.controller;

import com.ecommerce.auth.AuthAudience;
import com.ecommerce.auth.CurrentAccountData;
import com.ecommerce.auth.CurrentAccountService;
import com.ecommerce.order.OrderBulkDeleteRequest;
import com.ecommerce.order.OrderData;
import com.ecommerce.order.OrderExportRequest;
import com.ecommerce.order.OrderListData;
import com.ecommerce.order.OrderOverviewData;
import com.ecommerce.order.OrderRequest;
import com.ecommerce.order.OrderSettingsData;
import com.ecommerce.order.OrderSettingsRequest;
import com.ecommerce.order.StoreOrderService;
import com.ecommerce.order.StoreOrderSettingsService;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final StoreOrderService orderService;
    private final StoreOrderSettingsService settingsService;
    private final CurrentAccountService currentAccountService;

    public OrderController(
            StoreOrderService orderService,
            StoreOrderSettingsService settingsService,
            CurrentAccountService currentAccountService) {
        this.orderService = orderService;
        this.settingsService = settingsService;
        this.currentAccountService = currentAccountService;
    }

    // --- Order CRUD ---------------------------------------------------------

    @GetMapping("/{audience}/auth/orders")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String payment,
            @RequestParam(required = false) String fulfillment,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderListData data = orderService.list(
                scope.storeId(), search, payment, fulfillment, dateFrom, dateTo, customerName, page, size);
        return ok("Orders loaded", data);
    }

    @GetMapping("/{audience}/auth/orders/overview")
    public ResponseEntity<Map<String, Object>> overview(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderOverviewData data = orderService.overview(scope.storeId());
        return ok("Order overview loaded", data);
    }

    @GetMapping("/{audience}/auth/orders/{publicOrderId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String audience,
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderData data = orderService.get(scope.storeId(), publicOrderId);
        return ok("Order loaded", data);
    }

    @PostMapping("/{audience}/auth/orders")
    public ResponseEntity<Map<String, Object>> create(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OrderRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderData data = orderService.create(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Order created", data);
    }

    @PutMapping("/{audience}/auth/orders/{publicOrderId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String audience,
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OrderRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderData data = orderService.update(scope.storeId(), publicOrderId, request);
        return ok("Order updated", data);
    }

    @DeleteMapping("/{audience}/auth/orders/{publicOrderId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String audience,
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        orderService.delete(scope.storeId(), publicOrderId);
        return ok("Order deleted", null);
    }

    // --- Bulk actions -------------------------------------------------------

    @PostMapping("/{audience}/auth/orders/bulk-delete")
    public ResponseEntity<Map<String, Object>> bulkDelete(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OrderBulkDeleteRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        int deleted = orderService.bulkDelete(scope.storeId(), request == null ? null : request.ids());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        return ok("Orders deleted", data);
    }

    @PostMapping("/{audience}/auth/orders/export")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        byte[] data = orderService.exportCsv(scope.storeId(), request == null ? null : request.ids());
        return download(data, MediaType.parseMediaType("text/csv"), "orders-" + Instant.now().toEpochMilli() + ".csv");
    }

    // --- Invoice PDF --------------------------------------------------------

    @PostMapping("/{audience}/auth/orders/invoices/download")
    public ResponseEntity<byte[]> downloadInvoices(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request,
            @RequestParam(required = false) String templateId) {
        StoreScope scope = resolveScope(authorization, audience);
        byte[] data = orderService.invoicePdf(
                scope.storeId(), request == null ? null : request.ids(), templateId);
        return download(data, MediaType.APPLICATION_PDF, "order-invoices.pdf");
    }

    @GetMapping("/{audience}/auth/orders/{publicOrderId}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable String audience,
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String templateId) {
        StoreScope scope = resolveScope(authorization, audience);
        byte[] data = orderService.invoicePdf(scope.storeId(), java.util.List.of(publicOrderId), templateId);
        return download(data, MediaType.APPLICATION_PDF, publicOrderId + "-invoice.pdf");
    }

    // --- Shipping label PDF -------------------------------------------------

    @PostMapping("/{audience}/auth/orders/shipping-labels/download")
    public ResponseEntity<byte[]> downloadShippingLabels(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request,
            @RequestParam(required = false) String templateId) {
        StoreScope scope = resolveScope(authorization, audience);
        byte[] data = orderService.shippingLabelPdf(
                scope.storeId(), request == null ? null : request.ids(), templateId);
        return download(data, MediaType.APPLICATION_PDF, "shipping-labels.pdf");
    }

    @GetMapping("/{audience}/auth/orders/{publicOrderId}/shipping-label")
    public ResponseEntity<byte[]> downloadShippingLabel(
            @PathVariable String audience,
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String templateId) {
        StoreScope scope = resolveScope(authorization, audience);
        byte[] data = orderService.shippingLabelPdf(scope.storeId(), java.util.List.of(publicOrderId), templateId);
        return download(data, MediaType.APPLICATION_PDF, publicOrderId + "-shipping-label.pdf");
    }

    // --- Order Settings -----------------------------------------------------

    @GetMapping("/{audience}/auth/orders/settings")
    public ResponseEntity<Map<String, Object>> getSettings(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderSettingsData data = settingsService.get(scope.storeId());
        return ok("Order settings loaded", data);
    }

    @PostMapping("/{audience}/auth/orders/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(
            @PathVariable String audience,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OrderSettingsRequest request) {
        StoreScope scope = resolveScope(authorization, audience);
        OrderSettingsData data = settingsService.save(scope.storeId(), scope.ownerPublicUserId(), request);
        return ok("Order settings saved", data);
    }

    // --- Helpers ------------------------------------------------------------

    private StoreScope resolveScope(String authorization, String audience) {
        CurrentAccountData currentAccount = currentAccountService.resolveCurrentAccount(authorization);
        if (currentAccount.audience() != AuthAudience.from(audience)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audience mismatch");
        }
        if (currentAccount.store() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store setup required before managing orders");
        }
        return new StoreScope(currentAccount.store().storeId(), currentAccount.user().publicUserId());
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
