package com.ecommerce.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRedirectRepository extends JpaRepository<ProductRedirect, Long> {
    List<ProductRedirect> findByStoreIdOrderByCreatedAtDesc(String storeId);
}
