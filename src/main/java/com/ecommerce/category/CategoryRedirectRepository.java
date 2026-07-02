package com.ecommerce.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRedirectRepository extends JpaRepository<CategoryRedirect, Long> {
    List<CategoryRedirect> findByStoreIdOrderByCreatedAtDesc(String storeId);
}
