package com.ecommerce.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {
    List<InventoryLevel> findByStoreId(String storeId);

    List<InventoryLevel> findByStoreIdAndWarehousePublicId(String storeId, String warehousePublicId);

    List<InventoryLevel> findByStoreIdAndProductPublicId(String storeId, String productPublicId);

    Optional<InventoryLevel> findByStoreIdAndWarehousePublicIdAndProductPublicId(
            String storeId, String warehousePublicId, String productPublicId);

    void deleteByStoreIdAndWarehousePublicId(String storeId, String warehousePublicId);

    void deleteByStoreIdAndProductPublicId(String storeId, String productPublicId);
}
