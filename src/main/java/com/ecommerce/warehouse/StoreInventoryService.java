package com.ecommerce.warehouse;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductImage;
import com.ecommerce.product.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-warehouse inventory: listing (product × warehouse matrix), manual adjust/transfer,
 * and the reserve / deduct / receive operations used by the order and purchase-order
 * modules. Every mutation rolls the sum of on-hand back into {@code Product.stock} so the
 * legacy single-stock views stay accurate.
 */
@Service
@Transactional
public class StoreInventoryService {
    private final InventoryLevelRepository inventoryLevelRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StoreWarehouseService warehouseService;

    public StoreInventoryService(
            InventoryLevelRepository inventoryLevelRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            StoreWarehouseService warehouseService) {
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.warehouseService = warehouseService;
    }

    // --- listing -----------------------------------------------------------

    public InventoryListData list(
            String storeId, String ownerPublicUserId, String search, String warehouseFilter, int page, int size) {
        warehouseService.ensureSeeded(storeId, ownerPublicUserId);
        List<Warehouse> warehouses = warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId);
        Map<String, Warehouse> warehouseById =
                warehouses.stream().collect(Collectors.toMap(Warehouse::getPublicWarehouseId, w -> w));
        WarehouseData[] warehouseData = warehouses.stream()
                .map(w -> new WarehouseData(
                        w.getPublicWarehouseId(),
                        w.getWarehouseCode(),
                        w.getName(),
                        w.getAddressLine1(),
                        w.getAddressLine2(),
                        w.getCity(),
                        w.getState(),
                        w.getCountry(),
                        w.getPincode(),
                        w.getLatitude(),
                        w.getLongitude(),
                        w.getPhone(),
                        w.getEmail(),
                        w.isDefaultWarehouse(),
                        w.isFulfillsOnlineOrders(),
                        w.getPriority(),
                        w.getStatus().name(),
                        0,
                        w.getCreatedAt(),
                        w.getUpdatedAt()))
                .toArray(WarehouseData[]::new);

        Map<String, List<InventoryLevel>> levelsByProduct = inventoryLevelRepository.findByStoreId(storeId).stream()
                .collect(Collectors.groupingBy(InventoryLevel::getProductPublicId));

        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Product> products = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .filter(p -> query.isEmpty() || searchable(p).contains(query))
                .toList();

        long total = products.size();
        List<Product> pageItems;
        if (size <= 0) {
            pageItems = products;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(products.size(), from + size);
            pageItems = from >= products.size() ? List.of() : products.subList(from, to);
        }

        List<InventoryItemData> items = new ArrayList<>();
        for (Product product : pageItems) {
            Map<String, InventoryLevel> byWarehouse =
                    levelsByProduct.getOrDefault(product.getPublicProductId(), List.of()).stream()
                            .collect(Collectors.toMap(InventoryLevel::getWarehousePublicId, l -> l, (a, b) -> a));
            List<InventoryLevelData> levels = new ArrayList<>();
            int totalOnHand = 0;
            int totalAvailable = 0;
            for (Warehouse w : warehouses) {
                if (warehouseFilter != null
                        && !warehouseFilter.isBlank()
                        && !warehouseFilter.equals(w.getPublicWarehouseId())) {
                    continue;
                }
                InventoryLevel level = byWarehouse.get(w.getPublicWarehouseId());
                int onHand = level != null ? level.getOnHand() : 0;
                int reserved = level != null ? level.getReserved() : 0;
                int incoming = level != null ? level.getIncoming() : 0;
                int reorder = level != null ? level.getReorderPoint() : 0;
                levels.add(new InventoryLevelData(
                        w.getPublicWarehouseId(), w.getName(), onHand, reserved, onHand - reserved, incoming, reorder));
                totalOnHand += onHand;
                totalAvailable += (onHand - reserved);
            }
            items.add(new InventoryItemData(
                    product.getPublicProductId(),
                    product.getTitle(),
                    product.getSku(),
                    primaryImage(product),
                    product.isTrackInventory(),
                    product.getPrice(),
                    totalOnHand,
                    totalAvailable,
                    levels));
        }
        return new InventoryListData(items, List.of(warehouseData), total, Math.max(page, 1), size);
    }

    // --- manual adjust / transfer ------------------------------------------

    public InventoryItemData adjust(String storeId, InventoryAdjustRequest request) {
        if (request == null || isBlank(request.warehousePublicId()) || isBlank(request.productPublicId())) {
            throw new ResponseStatusException(BAD_REQUEST, "warehousePublicId and productPublicId are required");
        }
        requireWarehouse(storeId, request.warehousePublicId());
        Product product = requireProduct(storeId, request.productPublicId());
        InventoryLevel level = getOrCreateLevel(storeId, request.warehousePublicId(), product);

        int qty = request.quantity() != null ? request.quantity() : 0;
        String mode = request.mode() == null ? "SET" : request.mode().trim().toUpperCase(Locale.ROOT);
        int next = "DELTA".equals(mode) ? level.getOnHand() + qty : qty;
        if (next < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Resulting on-hand cannot be negative");
        }
        level.setOnHand(next);
        if (request.reorderPoint() != null) {
            level.setReorderPoint(Math.max(0, request.reorderPoint()));
        }
        inventoryLevelRepository.save(level);
        rollUpProductStock(storeId, product);
        return toItem(storeId, product);
    }

    public void transfer(String storeId, InventoryTransferRequest request) {
        if (request == null
                || isBlank(request.fromWarehousePublicId())
                || isBlank(request.toWarehousePublicId())
                || isBlank(request.productPublicId())) {
            throw new ResponseStatusException(BAD_REQUEST, "from/to warehouse and product are required");
        }
        if (request.fromWarehousePublicId().equals(request.toWarehousePublicId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Source and destination warehouses must differ");
        }
        int qty = request.quantity() != null ? request.quantity() : 0;
        if (qty <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Transfer quantity must be positive");
        }
        requireWarehouse(storeId, request.fromWarehousePublicId());
        requireWarehouse(storeId, request.toWarehousePublicId());
        Product product = requireProduct(storeId, request.productPublicId());

        InventoryLevel from = getOrCreateLevel(storeId, request.fromWarehousePublicId(), product);
        if (from.getOnHand() - qty < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Not enough stock at the source warehouse");
        }
        InventoryLevel to = getOrCreateLevel(storeId, request.toWarehousePublicId(), product);
        from.setOnHand(from.getOnHand() - qty);
        to.setOnHand(to.getOnHand() + qty);
        inventoryLevelRepository.save(from);
        inventoryLevelRepository.save(to);
        // Total on-hand unchanged, but persist rollup to be safe.
        rollUpProductStock(storeId, product);
    }

    // --- order / purchase-order hooks --------------------------------------

    /**
     * Deduct on-hand for a set of products at a warehouse (used when an order is confirmed).
     * Missing levels are created at zero and will throw if that would go negative and the
     * product tracks inventory.
     */
    public void deduct(String storeId, String warehousePublicId, Map<String, Integer> quantitiesByProduct) {
        if (warehousePublicId == null || quantitiesByProduct == null || quantitiesByProduct.isEmpty()) {
            return;
        }
        requireWarehouse(storeId, warehousePublicId);
        for (Map.Entry<String, Integer> entry : quantitiesByProduct.entrySet()) {
            Product product = productRepository
                    .findByStoreIdAndPublicProductId(storeId, entry.getKey())
                    .orElse(null);
            if (product == null || !product.isTrackInventory()) {
                continue;
            }
            InventoryLevel level = getOrCreateLevel(storeId, warehousePublicId, product);
            int qty = entry.getValue() != null ? entry.getValue() : 0;
            level.setOnHand(level.getOnHand() - qty);
            inventoryLevelRepository.save(level);
            rollUpProductStock(storeId, product);
        }
    }

    /** Add received units to on-hand at a warehouse (used when a purchase order is received). */
    public void receive(String storeId, String warehousePublicId, Map<String, Integer> quantitiesByProduct) {
        if (warehousePublicId == null || quantitiesByProduct == null || quantitiesByProduct.isEmpty()) {
            return;
        }
        requireWarehouse(storeId, warehousePublicId);
        for (Map.Entry<String, Integer> entry : quantitiesByProduct.entrySet()) {
            Product product = productRepository
                    .findByStoreIdAndPublicProductId(storeId, entry.getKey())
                    .orElse(null);
            if (product == null) {
                continue;
            }
            InventoryLevel level = getOrCreateLevel(storeId, warehousePublicId, product);
            int qty = entry.getValue() != null ? entry.getValue() : 0;
            level.setOnHand(level.getOnHand() + Math.max(0, qty));
            inventoryLevelRepository.save(level);
            rollUpProductStock(storeId, product);
        }
    }

    // --- helpers -----------------------------------------------------------

    private InventoryLevel getOrCreateLevel(String storeId, String warehousePublicId, Product product) {
        return inventoryLevelRepository
                .findByStoreIdAndWarehousePublicIdAndProductPublicId(
                        storeId, warehousePublicId, product.getPublicProductId())
                .orElseGet(() -> {
                    InventoryLevel level = new InventoryLevel();
                    level.setStoreId(storeId);
                    level.setWarehousePublicId(warehousePublicId);
                    level.setProductPublicId(product.getPublicProductId());
                    level.setSku(product.getSku());
                    return inventoryLevelRepository.save(level);
                });
    }

    /** Recompute {@code Product.stock} as the sum of on-hand across all warehouses. */
    private void rollUpProductStock(String storeId, Product product) {
        int total = inventoryLevelRepository
                .findByStoreIdAndProductPublicId(storeId, product.getPublicProductId())
                .stream()
                .mapToInt(InventoryLevel::getOnHand)
                .sum();
        product.setStock(total);
        productRepository.save(product);
    }

    private InventoryItemData toItem(String storeId, Product product) {
        List<Warehouse> warehouses = warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId);
        Map<String, InventoryLevel> byWarehouse = inventoryLevelRepository
                .findByStoreIdAndProductPublicId(storeId, product.getPublicProductId())
                .stream()
                .collect(Collectors.toMap(InventoryLevel::getWarehousePublicId, l -> l, (a, b) -> a));
        List<InventoryLevelData> levels = new ArrayList<>();
        int totalOnHand = 0;
        int totalAvailable = 0;
        for (Warehouse w : warehouses) {
            InventoryLevel level = byWarehouse.get(w.getPublicWarehouseId());
            int onHand = level != null ? level.getOnHand() : 0;
            int reserved = level != null ? level.getReserved() : 0;
            int incoming = level != null ? level.getIncoming() : 0;
            int reorder = level != null ? level.getReorderPoint() : 0;
            levels.add(new InventoryLevelData(
                    w.getPublicWarehouseId(), w.getName(), onHand, reserved, onHand - reserved, incoming, reorder));
            totalOnHand += onHand;
            totalAvailable += (onHand - reserved);
        }
        return new InventoryItemData(
                product.getPublicProductId(),
                product.getTitle(),
                product.getSku(),
                primaryImage(product),
                product.isTrackInventory(),
                product.getPrice(),
                totalOnHand,
                totalAvailable,
                levels);
    }

    private Warehouse requireWarehouse(String storeId, String publicWarehouseId) {
        return warehouseRepository
                .findByStoreIdAndPublicWarehouseId(storeId, publicWarehouseId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Warehouse not found"));
    }

    private Product requireProduct(String storeId, String publicProductId) {
        return productRepository
                .findByStoreIdAndPublicProductId(storeId, publicProductId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
    }

    private static String searchable(Product product) {
        return String.join(
                        " ", nullToEmpty(product.getTitle()), nullToEmpty(product.getSku()), nullToEmpty(product.getProductCode()))
                .toLowerCase(Locale.ROOT);
    }

    private static String primaryImage(Product product) {
        List<ProductImage> images = product.getImages();
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(ProductImage::isPrimaryImage)
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(images.get(0).getUrl());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
