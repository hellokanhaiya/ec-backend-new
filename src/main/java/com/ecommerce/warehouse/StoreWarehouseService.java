package com.ecommerce.warehouse;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.store.StoreProfile;
import com.ecommerce.store.StoreProfileRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Warehouse / fulfillment-location CRUD. A store always has at least one warehouse: the
 * first read triggers {@link #ensureSeeded} which creates a default warehouse from the
 * store's head-office address and backfills each product's {@code stock} into an
 * {@link InventoryLevel} at that warehouse.
 */
@Service
@Transactional
public class StoreWarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final StoreProfileRepository storeProfileRepository;
    private final ProductRepository productRepository;

    public StoreWarehouseService(
            WarehouseRepository warehouseRepository,
            InventoryLevelRepository inventoryLevelRepository,
            StoreProfileRepository storeProfileRepository,
            ProductRepository productRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.storeProfileRepository = storeProfileRepository;
        this.productRepository = productRepository;
    }

    // --- seeding -----------------------------------------------------------

    /**
     * Guarantee the store has a default warehouse. Idempotent — no-op once any warehouse
     * exists. On first run, seeds one from the store profile address and migrates existing
     * product stock into inventory levels so the two views stay consistent.
     */
    public Warehouse ensureSeeded(String storeId, String ownerPublicUserId) {
        return warehouseRepository
                .findFirstByStoreIdAndDefaultWarehouseTrue(storeId)
                .orElseGet(() -> {
                    if (warehouseRepository.countByStoreId(storeId) > 0) {
                        // Warehouses exist but none flagged default — promote the first.
                        Warehouse first = warehouseRepository
                                .findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId)
                                .get(0);
                        first.setDefaultWarehouse(true);
                        return warehouseRepository.save(first);
                    }
                    return seedFromProfile(storeId, ownerPublicUserId);
                });
    }

    private Warehouse seedFromProfile(String storeId, String ownerPublicUserId) {
        StoreProfile profile = storeProfileRepository.findByStoreId(storeId).orElse(null);
        Warehouse warehouse = new Warehouse();
        warehouse.setStoreId(storeId);
        warehouse.setOwnerPublicUserId(ownerPublicUserId);
        warehouse.setDefaultWarehouse(true);
        warehouse.setFulfillsOnlineOrders(true);
        warehouse.setPriority(0);
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        if (profile != null) {
            warehouse.setName(
                    profile.getBusinessName() != null ? profile.getBusinessName() + " — Primary" : "Primary location");
            warehouse.setAddressLine1(profile.getAddressLine1());
            warehouse.setAddressLine2(profile.getAddressLine2());
            warehouse.setCity(profile.getCity());
            warehouse.setState(profile.getState());
            warehouse.setCountry(profile.getCountryCode());
            warehouse.setPincode(profile.getPostalCode());
            warehouse.setPhone(profile.getBusinessPhone());
            warehouse.setEmail(profile.getBusinessEmail());
        } else {
            warehouse.setName("Primary location");
        }
        Warehouse saved = warehouseRepository.save(warehouse);
        if (saved.getWarehouseCode() == null || saved.getWarehouseCode().isBlank()) {
            saved.setWarehouseCode(String.format("WH-%05d", saved.getId()));
            saved = warehouseRepository.save(saved);
        }
        backfillInventory(storeId, saved.getPublicWarehouseId());
        return saved;
    }

    /** Copy each product's current {@code stock} into an inventory level at the seed warehouse. */
    private void backfillInventory(String storeId, String warehousePublicId) {
        List<Product> products = productRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        for (Product product : products) {
            if (inventoryLevelRepository
                    .findByStoreIdAndWarehousePublicIdAndProductPublicId(
                            storeId, warehousePublicId, product.getPublicProductId())
                    .isPresent()) {
                continue;
            }
            InventoryLevel level = new InventoryLevel();
            level.setStoreId(storeId);
            level.setWarehousePublicId(warehousePublicId);
            level.setProductPublicId(product.getPublicProductId());
            level.setSku(product.getSku());
            level.setOnHand(product.getStock() != null ? Math.max(0, product.getStock()) : 0);
            inventoryLevelRepository.save(level);
        }
    }

    // --- CRUD --------------------------------------------------------------

    public WarehouseListData list(String storeId, String ownerPublicUserId, int page, int size) {
        ensureSeeded(storeId, ownerPublicUserId);
        List<Warehouse> all = warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId);
        Map<String, Integer> onHandByWarehouse = onHandByWarehouse(storeId);

        long total = all.size();
        List<Warehouse> pageItems;
        if (size <= 0) {
            pageItems = all;
        } else {
            int from = Math.max(0, (Math.max(page, 1) - 1) * size);
            int to = Math.min(all.size(), from + size);
            pageItems = from >= all.size() ? List.of() : all.subList(from, to);
        }
        List<WarehouseData> items =
                pageItems.stream().map(w -> toData(w, onHandByWarehouse)).toList();
        return new WarehouseListData(items, total, Math.max(page, 1), size);
    }

    public long count(String storeId, String ownerPublicUserId) {
        ensureSeeded(storeId, ownerPublicUserId);
        return warehouseRepository.countByStoreId(storeId);
    }

    public WarehouseData get(String storeId, String publicWarehouseId) {
        return toData(require(storeId, publicWarehouseId), onHandByWarehouse(storeId));
    }

    public WarehouseData create(String storeId, String ownerPublicUserId, WarehouseRequest request) {
        ensureSeeded(storeId, ownerPublicUserId);
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setStoreId(storeId);
        warehouse.setOwnerPublicUserId(ownerPublicUserId);
        applyRequest(warehouse, request);
        Warehouse saved = warehouseRepository.save(warehouse);
        if (saved.getWarehouseCode() == null || saved.getWarehouseCode().isBlank()) {
            saved.setWarehouseCode(String.format("WH-%05d", saved.getId()));
            saved = warehouseRepository.save(saved);
        }
        enforceSingleDefault(storeId, saved);
        return toData(saved, onHandByWarehouse(storeId));
    }

    public WarehouseData update(String storeId, String publicWarehouseId, WarehouseRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        Warehouse warehouse = require(storeId, publicWarehouseId);
        applyRequest(warehouse, request);
        Warehouse saved = warehouseRepository.save(warehouse);
        enforceSingleDefault(storeId, saved);
        return toData(saved, onHandByWarehouse(storeId));
    }

    public void delete(String storeId, String publicWarehouseId) {
        Warehouse warehouse = require(storeId, publicWarehouseId);
        if (warehouse.isDefaultWarehouse()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete the default warehouse. Set another as default first.");
        }
        if (warehouseRepository.countByStoreId(storeId) <= 1) {
            throw new ResponseStatusException(BAD_REQUEST, "A store must keep at least one warehouse");
        }
        inventoryLevelRepository.deleteByStoreIdAndWarehousePublicId(storeId, publicWarehouseId);
        warehouseRepository.delete(warehouse);
    }

    public WarehouseData setDefault(String storeId, String publicWarehouseId) {
        Warehouse target = require(storeId, publicWarehouseId);
        for (Warehouse w : warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId)) {
            boolean shouldBeDefault = w.getId().equals(target.getId());
            if (w.isDefaultWarehouse() != shouldBeDefault) {
                w.setDefaultWarehouse(shouldBeDefault);
                warehouseRepository.save(w);
            }
        }
        return toData(require(storeId, publicWarehouseId), onHandByWarehouse(storeId));
    }

    /**
     * Resolve the warehouse an order/PO should use. If {@code requested} is given it must
     * exist; otherwise the store's default is used, unless the store has more than one
     * warehouse in which case an explicit choice is required.
     */
    public String resolveFulfillmentWarehouse(String storeId, String ownerPublicUserId, String requested) {
        Warehouse seeded = ensureSeeded(storeId, ownerPublicUserId);
        if (requested != null && !requested.isBlank()) {
            return require(storeId, requested).getPublicWarehouseId();
        }
        if (warehouseRepository.countByStoreId(storeId) > 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Select a warehouse to fulfill this order from");
        }
        return seeded.getPublicWarehouseId();
    }

    // --- helpers -----------------------------------------------------------

    Warehouse require(String storeId, String publicWarehouseId) {
        return warehouseRepository
                .findByStoreIdAndPublicWarehouseId(storeId, publicWarehouseId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Warehouse not found"));
    }

    private void applyRequest(Warehouse warehouse, WarehouseRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        if (name == null || name.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Warehouse name is required");
        }
        warehouse.setName(name);
        warehouse.setAddressLine1(normalize(request.addressLine1()));
        warehouse.setAddressLine2(normalize(request.addressLine2()));
        warehouse.setCity(normalize(request.city()));
        warehouse.setState(normalize(request.state()));
        warehouse.setCountry(normalize(request.country()));
        warehouse.setPincode(normalize(request.pincode()));
        warehouse.setLatitude(request.latitude());
        warehouse.setLongitude(request.longitude());
        warehouse.setPhone(normalize(request.phone()));
        warehouse.setEmail(normalize(request.email()));
        if (request.defaultWarehouse() != null) {
            warehouse.setDefaultWarehouse(request.defaultWarehouse());
        }
        warehouse.setFulfillsOnlineOrders(
                request.fulfillsOnlineOrders() == null || request.fulfillsOnlineOrders());
        warehouse.setPriority(request.priority() != null ? request.priority() : warehouse.getPriority());
        warehouse.setStatus(WarehouseStatus.from(request.status()));
    }

    /** Ensure exactly one default exists; if {@code saved} was just marked default, clear others. */
    private void enforceSingleDefault(String storeId, Warehouse saved) {
        if (!saved.isDefaultWarehouse()) {
            // Guarantee at least one default remains.
            if (warehouseRepository.findFirstByStoreIdAndDefaultWarehouseTrue(storeId).isEmpty()) {
                saved.setDefaultWarehouse(true);
                warehouseRepository.save(saved);
            }
            return;
        }
        for (Warehouse w : warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId)) {
            if (!w.getId().equals(saved.getId()) && w.isDefaultWarehouse()) {
                w.setDefaultWarehouse(false);
                warehouseRepository.save(w);
            }
        }
    }

    private Map<String, Integer> onHandByWarehouse(String storeId) {
        return inventoryLevelRepository.findByStoreId(storeId).stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getWarehousePublicId, Collectors.summingInt(InventoryLevel::getOnHand)));
    }

    private WarehouseData toData(Warehouse w, Map<String, Integer> onHandByWarehouse) {
        return new WarehouseData(
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
                onHandByWarehouse.getOrDefault(w.getPublicWarehouseId(), 0),
                w.getCreatedAt(),
                w.getUpdatedAt());
    }

    /** Shared with other services that need warehouse data keyed by public id. */
    public Map<String, Warehouse> warehousesById(String storeId) {
        return warehouseRepository.findByStoreIdOrderByPriorityAscCreatedAtAsc(storeId).stream()
                .collect(Collectors.toMap(Warehouse::getPublicWarehouseId, Function.identity()));
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
