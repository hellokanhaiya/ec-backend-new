package com.ecommerce.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    List<StoreOrder> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<StoreOrder> findByStoreIdAndPublicOrderId(String storeId, String publicOrderId);
}
