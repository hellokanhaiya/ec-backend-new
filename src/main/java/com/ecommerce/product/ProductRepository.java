package com.ecommerce.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<Product> findByStoreIdAndPublicProductId(String storeId, String publicProductId);

    Optional<Product> findByStoreIdAndSkuIgnoreCase(String storeId, String sku);
}
