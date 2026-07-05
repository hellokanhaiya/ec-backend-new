package com.ecommerce.purchase;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderNumberSequenceRepository extends JpaRepository<PurchaseOrderNumberSequence, Long> {
    Optional<PurchaseOrderNumberSequence> findByStoreId(String storeId);
}
