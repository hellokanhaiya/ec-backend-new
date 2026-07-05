package com.ecommerce.purchase;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSupplierRepository extends JpaRepository<PurchaseSupplier, Long> {
    List<PurchaseSupplier> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<PurchaseSupplier> findByStoreIdAndPublicSupplierId(String storeId, String publicSupplierId);
}
