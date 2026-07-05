package com.ecommerce.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByStoreIdOrderByPriorityAscCreatedAtAsc(String storeId);

    Optional<Warehouse> findByStoreIdAndPublicWarehouseId(String storeId, String publicWarehouseId);

    Optional<Warehouse> findFirstByStoreIdAndDefaultWarehouseTrue(String storeId);

    long countByStoreId(String storeId);
}
