package com.ecommerce.purchase;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<PurchaseOrder> findByStoreIdAndPublicPurchaseOrderId(String storeId, String publicPurchaseOrderId);
}
