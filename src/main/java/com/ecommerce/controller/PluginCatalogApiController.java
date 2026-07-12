package com.ecommerce.controller;

import com.ecommerce.bulk.ProductCsvService;
import com.ecommerce.bundle.StoreProductBundleService;
import com.ecommerce.category.StoreCategoryService;
import com.ecommerce.media.StoreMediaService;
import com.ecommerce.plugin.PluginAccessControlService;
import com.ecommerce.plugin.PluginAccessScope;
import com.ecommerce.plugin.PluginApiResponses;
import com.ecommerce.plugin.PluginExportRequest;
import com.ecommerce.plugin.PluginScopeCatalog;
import com.ecommerce.product.ProductData;
import com.ecommerce.product.StoreProductService;
import com.ecommerce.tag.StoreTagService;
import com.ecommerce.warehouse.StoreInventoryService;
import com.ecommerce.warehouse.StoreWarehouseService;
import java.time.Instant;
import java.util.List;
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
 * Read-only plugin API for the catalog: products, categories, bundles, inventory, warehouses,
 * media, and tags. Authenticates only via {@code sk_plg_} plugin tokens; contains no write
 * handlers. Reuses the same storeId-scoped services the dashboard controllers use.
 */
@RestController
@RequestMapping("/api/v1/plugin")
public class PluginCatalogApiController {
    private final StoreProductService productService;
    private final ProductCsvService productCsvService;
    private final StoreCategoryService categoryService;
    private final StoreProductBundleService bundleService;
    private final StoreInventoryService inventoryService;
    private final StoreWarehouseService warehouseService;
    private final StoreMediaService mediaService;
    private final StoreTagService tagService;
    private final PluginAccessControlService pluginAccessControl;

    public PluginCatalogApiController(
            StoreProductService productService,
            ProductCsvService productCsvService,
            StoreCategoryService categoryService,
            StoreProductBundleService bundleService,
            StoreInventoryService inventoryService,
            StoreWarehouseService warehouseService,
            StoreMediaService mediaService,
            StoreTagService tagService,
            PluginAccessControlService pluginAccessControl) {
        this.productService = productService;
        this.productCsvService = productCsvService;
        this.categoryService = categoryService;
        this.bundleService = bundleService;
        this.inventoryService = inventoryService;
        this.warehouseService = warehouseService;
        this.mediaService = mediaService;
        this.tagService = tagService;
        this.pluginAccessControl = pluginAccessControl;
    }

    // --- Products -----------------------------------------------------------

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> listProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String minPrice,
            @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String stockLevel,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PRODUCTS_READ);
        var data = productService.list(
                scope.storeId(), search, status, category, vendor, minPrice, maxPrice, stockLevel,
                sort, dateFrom, dateTo, page, size);
        return PluginApiResponses.ok("Products loaded", data);
    }

    @GetMapping("/products/overview")
    public ResponseEntity<Map<String, Object>> productsOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PRODUCTS_READ);
        return PluginApiResponses.ok("Products overview loaded", productService.overview(scope.storeId()));
    }

    @GetMapping("/products/{publicProductId}")
    public ResponseEntity<Map<String, Object>> getProduct(
            @PathVariable String publicProductId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PRODUCTS_READ);
        return PluginApiResponses.ok("Product loaded", productService.get(scope.storeId(), publicProductId));
    }

    @PostMapping("/products/export")
    public ResponseEntity<byte[]> exportProducts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PluginExportRequest request,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.PRODUCTS_EXPORT);
        List<ProductData> products = productService.exportProducts(
                scope.storeId(),
                request == null ? null : request.ids(),
                request == null ? null : request.skus(),
                all,
                search,
                status,
                category);
        byte[] csv = productCsvService.write(products);
        return PluginApiResponses.download(
                csv, MediaType.parseMediaType("text/csv"), "products-" + Instant.now().toEpochMilli() + ".csv");
    }

    // --- Categories ---------------------------------------------------------

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> listCategories(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CATEGORIES_READ);
        return PluginApiResponses.ok("Categories loaded", categoryService.list(scope.storeId(), search));
    }

    @GetMapping("/categories/{publicCategoryId}")
    public ResponseEntity<Map<String, Object>> getCategory(
            @PathVariable String publicCategoryId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.CATEGORIES_READ);
        return PluginApiResponses.ok("Category loaded", categoryService.get(scope.storeId(), publicCategoryId));
    }

    // --- Bundles ------------------------------------------------------------

    @GetMapping("/bundles")
    public ResponseEntity<Map<String, Object>> listBundles(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BUNDLES_READ);
        return PluginApiResponses.ok(
                "Bundles loaded", bundleService.list(scope.storeId(), search, type, status, page, size));
    }

    @GetMapping("/bundles/{publicBundleId}")
    public ResponseEntity<Map<String, Object>> getBundle(
            @PathVariable String publicBundleId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.BUNDLES_READ);
        return PluginApiResponses.ok("Bundle loaded", bundleService.get(scope.storeId(), publicBundleId));
    }

    // --- Inventory ----------------------------------------------------------

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> listInventory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String warehouse,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "0") int size,
            @RequestParam(required = false) String skus) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.INVENTORY_READ);
        var data = inventoryService.list(
                scope.storeId(), scope.ownerPublicUserId(), search, warehouse, page, size, skus);
        return PluginApiResponses.ok("Inventory loaded", data);
    }

    // --- Warehouses ---------------------------------------------------------

    @GetMapping("/warehouses")
    public ResponseEntity<Map<String, Object>> listWarehouses(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.WAREHOUSES_READ);
        return PluginApiResponses.ok(
                "Warehouses loaded", warehouseService.list(scope.storeId(), scope.ownerPublicUserId(), page, size));
    }

    @GetMapping("/warehouses/{publicWarehouseId}")
    public ResponseEntity<Map<String, Object>> getWarehouse(
            @PathVariable String publicWarehouseId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.WAREHOUSES_READ);
        return PluginApiResponses.ok("Warehouse loaded", warehouseService.get(scope.storeId(), publicWarehouseId));
    }

    // --- Media --------------------------------------------------------------

    @GetMapping("/media")
    public ResponseEntity<Map<String, Object>> listMedia(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String productSearch,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.MEDIA_READ);
        var data = mediaService.list(
                scope.storeId(), search, sort, sortDir, mediaType, format, source, category, productSearch,
                minSize, maxSize, dateFrom, dateTo, page, size);
        return PluginApiResponses.ok("Media loaded", data);
    }

    @GetMapping("/media/{publicMediaId}")
    public ResponseEntity<Map<String, Object>> getMedia(
            @PathVariable String publicMediaId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.MEDIA_READ);
        return PluginApiResponses.ok("Media asset loaded", mediaService.get(scope.storeId(), publicMediaId));
    }

    // --- Tags ---------------------------------------------------------------

    @GetMapping("/tags")
    public ResponseEntity<Map<String, Object>> listTags(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        PluginAccessScope scope =
                pluginAccessControl.requirePluginScope(authorization, PluginScopeCatalog.TAGS_READ);
        return PluginApiResponses.ok("Tags loaded", tagService.list(scope.storeId()));
    }
}
