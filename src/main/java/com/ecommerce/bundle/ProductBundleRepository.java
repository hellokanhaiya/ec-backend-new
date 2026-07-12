package com.ecommerce.bundle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {
    List<ProductBundle> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<ProductBundle> findByStoreIdAndPublicBundleId(String storeId, String publicBundleId);

    Optional<ProductBundle> findByStoreIdAndSkuIgnoreCase(String storeId, String sku);
}
