package com.ecommerce.controller;

import com.ecommerce.abandoned.AbandonedCartService;
import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginApiResponses;
import com.ecommerce.plugin.PluginExportRequest;
import com.ecommerce.plugin.PluginScopeCatalog;
import com.ecommerce.promotion.StorePromotionService;
import com.ecommerce.purchase.StorePurchaseOrderService;
import com.ecommerce.shipping.PincodeRange;
import com.ecommerce.shipping.StoreShippingService;
import com.ecommerce.vendor.VendorService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Read-only plugin API for operations: purchase orders, abandoned carts, vendors, shipping
 * configuration, and discounts. Plugin-token auth only; no write handlers.
 */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginOperationsApiController {
    private final StorePurchaseOrderService purchaseOrderService;
    private final AbandonedCartService abandonedCartService;
    private final VendorService vendorService;
    private final StoreShippingService shippingService;
    private final StorePromotionService promotionService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginOperationsApiController(
            StorePurchaseOrderService purchaseOrderService,
            AbandonedCartService abandonedCartService,
            VendorService vendorService,
            StoreShippingService shippingService,
            StorePromotionService promotionService,
            PluginAccessControlService pluginAccessControl) {
        this.purchaseOrderService = purchaseOrderService;
        this.abandonedCartService = abandonedCartService;
        this.vendorService = vendorService;
        this.shippingService = shippingService;
        this.promotionService = promotionService;
        this.pluginAccessControl = pluginAccessControl;
    }

    // --- Purchase orders ----------------------------------------------------

    @GetMapping("/purchase-orders")
    public ResponseEntity<Map<String, Object>> listPurchaseOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierSearch,
            @RequestParam(required = false) String supplierIds,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_READ);
        var data = purchaseOrderService.list(
                scope.storeId(), search, status, supplierSearch, supplierIds, dateFrom, dateTo, page, size);
        return PluginApiResponses.ok("Purchase orders loaded", data);
    }

    @GetMapping("/purchase-orders/overview")
    public ResponseEntity<Map<String, Object>> purchaseOrdersOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_READ);
        return PluginApiResponses.ok(
                "Purchase orders overview loaded", purchaseOrderService.overview(scope.storeId()));
    }

    @GetMapping("/purchase-orders/suppliers")
    public ResponseEntity<Map<String, Object>> listSuppliers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_READ);
        return PluginApiResponses.ok(
                "Suppliers loaded", purchaseOrderService.listSuppliers(scope.storeId(), search, page, size));
    }

    @GetMapping("/purchase-orders/suppliers/{publicSupplierId}")
    public ResponseEntity<Map<String, Object>> getSupplier(
            @PathVariable String publicSupplierId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_READ);
        return PluginApiResponses.ok(
                "Supplier loaded", purchaseOrderService.getSupplier(scope.storeId(), publicSupplierId));
    }

    @GetMapping("/purchase-orders/{publicPurchaseOrderId}")
    public ResponseEntity<Map<String, Object>> getPurchaseOrder(
            @PathVariable String publicPurchaseOrderId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_READ);
        return PluginApiResponses.ok(
                "Purchase order loaded", purchaseOrderService.get(scope.storeId(), publicPurchaseOrderId));
    }

    @PostMapping("/purchase-orders/export")
    public ResponseEntity<byte[]> exportPurchaseOrders(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PluginExportRequest request) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PURCHASE_ORDERS_EXPORT);
        byte[] csv = purchaseOrderService.exportCsv(scope.storeId(), request == null ? null : request.ids());
        return PluginApiResponses.download(
                csv, MediaType.parseMediaType("text/csv"),
                "purchase-orders-" + Instant.now().toEpochMilli() + ".csv");
    }

    // --- Abandoned carts ----------------------------------------------------

    @GetMapping("/abandoned-carts")
    public ResponseEntity<Map<String, Object>> listAbandonedCarts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ABANDONED_CARTS_READ);
        var data = abandonedCartService.list(scope.storeId(), scope.ownerPublicUserId(), search, status, page, size);
        return PluginApiResponses.ok("Abandoned carts loaded", data);
    }

    @GetMapping("/abandoned-carts/overview")
    public ResponseEntity<Map<String, Object>> abandonedCartsOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ABANDONED_CARTS_READ);
        return PluginApiResponses.ok(
                "Abandoned carts overview loaded",
                abandonedCartService.overview(scope.storeId(), scope.ownerPublicUserId()));
    }

    @GetMapping("/abandoned-carts/{publicCartId}")
    public ResponseEntity<Map<String, Object>> getAbandonedCart(
            @PathVariable String publicCartId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.ABANDONED_CARTS_READ);
        return PluginApiResponses.ok(
                "Abandoned cart loaded", abandonedCartService.get(scope.storeId(), publicCartId));
    }

    // --- Vendors ------------------------------------------------------------

    @GetMapping("/vendors")
    public ResponseEntity<Map<String, Object>> listVendors(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.VENDORS_READ);
        var data = vendorService.list(scope.storeId(), scope.ownerPublicUserId(), search, status, page, size);
        return PluginApiResponses.ok("Vendors loaded", data);
    }

    @GetMapping("/vendors/overview")
    public ResponseEntity<Map<String, Object>> vendorsOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.VENDORS_READ);
        return PluginApiResponses.ok(
                "Vendors overview loaded", vendorService.overview(scope.storeId(), scope.ownerPublicUserId()));
    }

    @GetMapping("/vendors/{publicVendorId}")
    public ResponseEntity<Map<String, Object>> getVendor(
            @PathVariable String publicVendorId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.VENDORS_READ);
        return PluginApiResponses.ok("Vendor loaded", vendorService.get(scope.storeId(), publicVendorId));
    }

    // --- Shipping (read-only config) ----------------------------------------

    @GetMapping("/shipping/settings")
    public ResponseEntity<Map<String, Object>> shippingSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.SHIPPING_READ);
        return PluginApiResponses.ok(
                "Shipping settings loaded", shippingService.settings(scope.storeId(), scope.ownerPublicUserId()));
    }

    @GetMapping("/shipping/delivery-settings")
    public ResponseEntity<Map<String, Object>> deliverySettings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.SHIPPING_READ);
        return PluginApiResponses.ok(
                "Delivery settings loaded", shippingService.getDeliverySettings(scope.storeId()));
    }

    @GetMapping("/shipping/serviceability")
    public ResponseEntity<Map<String, Object>> listPincodes(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String zone) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.SHIPPING_READ);
        return PluginApiResponses.ok(
                "Serviceability loaded",
                shippingService.listPincodeRanges(scope.storeId(), scope.ownerPublicUserId(), zone));
    }

    @GetMapping("/shipping/serviceability/check")
    public ResponseEntity<Map<String, Object>> checkPincode(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String pincode) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.SHIPPING_READ);
        PincodeRange match = shippingService.matchPincode(scope.storeId(), pincode).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceable", match != null);
        data.put("zonePublicId", match == null ? null : match.getZonePublicId());
        data.put("codAvailable", match != null && match.isCodAvailable());
        data.put("etaMinDays", match == null ? null : match.getEtaMinDays());
        data.put("etaMaxDays", match == null ? null : match.getEtaMaxDays());
        return PluginApiResponses.ok("Serviceability checked", data);
    }

    // --- Discounts ----------------------------------------------------------

    @GetMapping("/discounts")
    public ResponseEntity<Map<String, Object>> listDiscounts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.DISCOUNTS_READ);
        var data = promotionService.list(scope.storeId(), search, type, status, page, size);
        return PluginApiResponses.ok("Discounts loaded", data);
    }

    @GetMapping("/discounts/{publicPromotionId}")
    public ResponseEntity<Map<String, Object>> getDiscount(
            @PathVariable String publicPromotionId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.DISCOUNTS_READ);
        return PluginApiResponses.ok("Discount loaded", promotionService.get(scope.storeId(), publicPromotionId));
    }
}
