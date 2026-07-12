package com.ecommerce.controller;

import com.ecommerce.order.OrderData;
import com.ecommerce.order.OrderExportRequest;
import com.ecommerce.order.OrderListData;
import com.ecommerce.order.StoreOrderService;
import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginScopeCatalog;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public plugin API surface. Authenticates exclusively via {@code sk_plg_} plugin tokens
 * through {@link PluginAccessControlService} — dashboard session tokens are rejected. This
 * namespace deliberately contains only read/export/PDF handlers; order writes, user management,
 * and every other admin capability live solely on the dashboard surface and are structurally
 * unreachable with a plugin token.
 */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginApiController {
    private final StoreOrderService orderService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginApiController(
            StoreOrderService orderService, PluginAccessControlService pluginAccessControl) {
        this.orderService = orderService;
        this.pluginAccessControl = pluginAccessControl;
    }

    // --- Orders (read) ------------------------------------------------------

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payment,
            @RequestParam(required = false) String fulfillment,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String customerName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ORDERS_READ);
        OrderListData data = orderService.list(
                scope.storeId(), search, status, payment, fulfillment, dateFrom, dateTo, customerName, page, size);
        return ok("Orders loaded", data);
    }

    @GetMapping("/orders/{publicOrderId}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ORDERS_READ);
        OrderData data = orderService.get(scope.storeId(), publicOrderId);
        return ok("Order loaded", data);
    }

    // --- Export & documents -------------------------------------------------

    @PostMapping("/orders/export")
    public ResponseEntity<byte[]> exportOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ORDERS_EXPORT);
        byte[] data = orderService.exportCsv(scope.storeId(), request == null ? null : request.ids());
        return download(data, MediaType.parseMediaType("text/csv"),
                "orders-" + Instant.now().toEpochMilli() + ".csv");
    }

    @PostMapping("/orders/shipping-labels")
    public ResponseEntity<byte[]> shippingLabels(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request,
            @RequestParam(required = false) String templateId) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.LABELS_GENERATE);
        byte[] data = orderService.shippingLabelPdf(
                scope.storeId(), request == null ? null : request.ids(), templateId);
        return download(data, MediaType.APPLICATION_PDF, "shipping-labels.pdf");
    }

    @GetMapping("/orders/{publicOrderId}/shipping-label")
    public ResponseEntity<byte[]> shippingLabel(
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String templateId) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.LABELS_GENERATE);
        byte[] data = orderService.shippingLabelPdf(scope.storeId(), List.of(publicOrderId), templateId);
        return download(data, MediaType.APPLICATION_PDF, publicOrderId + "-shipping-label.pdf");
    }

    @PostMapping("/orders/invoices")
    public ResponseEntity<byte[]> invoices(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) OrderExportRequest request,
            @RequestParam(required = false) String templateId) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.INVOICES_GENERATE);
        byte[] data = orderService.invoicePdf(
                scope.storeId(), request == null ? null : request.ids(), templateId);
        return download(data, MediaType.APPLICATION_PDF, "order-invoices.pdf");
    }

    @GetMapping("/orders/{publicOrderId}/invoice")
    public ResponseEntity<byte[]> invoice(
            @PathVariable String publicOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String templateId) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.INVOICES_GENERATE);
        byte[] data = orderService.invoicePdf(scope.storeId(), List.of(publicOrderId), templateId);
        return download(data, MediaType.APPLICATION_PDF, publicOrderId + "-invoice.pdf");
    }

    // --- Helpers ------------------------------------------------------------

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
}
