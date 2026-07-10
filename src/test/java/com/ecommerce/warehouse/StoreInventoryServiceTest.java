package com.ecommerce.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.product.Product;
import com.ecommerce.product.ProductRepository;
import com.ecommerce.store.StoreProfileRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
        properties = {
            "spring.config.import=",
            "spring.datasource.url=jdbc:h2:mem:inventory;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password="
        })
class StoreInventoryServiceTest {
    private static final String STORE = "store-1";
    private static final String OWNER = "owner-1";

    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private InventoryLevelRepository inventoryLevelRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreProfileRepository storeProfileRepository;

    private StoreWarehouseService warehouseService;
    private StoreInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        warehouseService = new StoreWarehouseService(
                warehouseRepository, inventoryLevelRepository, storeProfileRepository, productRepository);
        inventoryService = new StoreInventoryService(
                inventoryLevelRepository, warehouseRepository, productRepository, warehouseService);
    }

    private Product saveProduct(String title, String sku, int stock) {
        Product product = new Product();
        product.setStoreId(STORE);
        product.setOwnerPublicUserId(OWNER);
        product.setTitle(title);
        product.setSku(sku);
        product.setStock(stock);
        return productRepository.save(product);
    }

    @Test
    void seedsDefaultWarehouseAndBackfillsProductStock() {
        Product product = saveProduct("Kurta", "SKU-1", 25);

        var list = inventoryService.list(STORE, OWNER, null, null, 1, 0, null);

        assertThat(warehouseRepository.countByStoreId(STORE)).isEqualTo(1);
        assertThat(list.warehouses()).hasSize(1);
        assertThat(list.items()).hasSize(1);
        assertThat(list.items().get(0).totalOnHand()).isEqualTo(25);
        // backfilled into an inventory level at the default warehouse
        assertThat(inventoryLevelRepository.findByStoreIdAndProductPublicId(STORE, product.getPublicProductId()))
                .hasSize(1);
    }

    @Test
    void adjustSetAndDeltaUpdateOnHandAndRollUp() {
        Product product = saveProduct("Saree", "SKU-2", 0);
        warehouseService.ensureSeeded(STORE, OWNER);
        String wh = warehouseRepository.findFirstByStoreIdAndDefaultWarehouseTrue(STORE).orElseThrow()
                .getPublicWarehouseId();

        inventoryService.adjust(STORE, new InventoryAdjustRequest(wh, product.getPublicProductId(), "SET", 100, 10, null));
        inventoryService.adjust(STORE, new InventoryAdjustRequest(wh, product.getPublicProductId(), "DELTA", -30, null, null));

        assertThat(productRepository.findByStoreIdAndPublicProductId(STORE, product.getPublicProductId())
                        .orElseThrow()
                        .getStock())
                .isEqualTo(70);
    }

    @Test
    void adjustRejectsNegativeResult() {
        Product product = saveProduct("Bag", "SKU-3", 5);
        warehouseService.ensureSeeded(STORE, OWNER);
        String wh = warehouseRepository.findFirstByStoreIdAndDefaultWarehouseTrue(STORE).orElseThrow()
                .getPublicWarehouseId();

        assertThatThrownBy(() -> inventoryService.adjust(
                        STORE, new InventoryAdjustRequest(wh, product.getPublicProductId(), "DELTA", -999, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void transferMovesStockBetweenWarehousesKeepingTotal() {
        Product product = saveProduct("Lamp", "SKU-4", 40);
        warehouseService.ensureSeeded(STORE, OWNER);
        String from = warehouseRepository.findFirstByStoreIdAndDefaultWarehouseTrue(STORE).orElseThrow()
                .getPublicWarehouseId();
        var second = warehouseService.create(
                STORE,
                OWNER,
                new WarehouseRequest("North DC", null, null, null, null, "IN", "110001", null, null, null, null,
                        false, true, 1, "ACTIVE"));
        String to = second.publicWarehouseId();

        inventoryService.transfer(STORE, new InventoryTransferRequest(from, to, product.getPublicProductId(), 15));

        var byWarehouse = inventoryLevelRepository
                .findByStoreIdAndProductPublicId(STORE, product.getPublicProductId());
        Map<String, Integer> onHand = byWarehouse.stream()
                .collect(java.util.stream.Collectors.toMap(
                        InventoryLevel::getWarehousePublicId, InventoryLevel::getOnHand));
        assertThat(onHand.get(from)).isEqualTo(25);
        assertThat(onHand.get(to)).isEqualTo(15);
        assertThat(productRepository.findByStoreIdAndPublicProductId(STORE, product.getPublicProductId())
                        .orElseThrow()
                        .getStock())
                .isEqualTo(40);
    }

    @Test
    void deductDrawsDownStockForOrder() {
        Product product = saveProduct("Mug", "SKU-5", 12);
        warehouseService.ensureSeeded(STORE, OWNER);
        String wh = warehouseRepository.findFirstByStoreIdAndDefaultWarehouseTrue(STORE).orElseThrow()
                .getPublicWarehouseId();

        inventoryService.deduct(STORE, wh, Map.of(product.getPublicProductId(), 5));

        assertThat(productRepository.findByStoreIdAndPublicProductId(STORE, product.getPublicProductId())
                        .orElseThrow()
                        .getStock())
                .isEqualTo(7);
    }

    @Test
    void countReflectsMultipleWarehouses() {
        warehouseService.ensureSeeded(STORE, OWNER);
        warehouseService.create(
                STORE,
                OWNER,
                new WarehouseRequest("South DC", null, null, null, null, "IN", "560001", null, null, null, null,
                        false, true, 2, "ACTIVE"));

        assertThat(warehouseService.count(STORE, OWNER)).isEqualTo(2);
        assertThat(warehouseService.list(STORE, OWNER, 1, 0).items()).extracting(w -> w.name())
                .contains("South DC");
    }
}
