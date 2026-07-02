package com.ecommerce.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByStoreIdOrderByCreatedAtDesc(String storeId);

    List<Category> findByStoreIdOrderBySortPositionAscCreatedAtDesc(String storeId);

    Optional<Category> findByStoreIdAndPublicCategoryId(String storeId, String publicCategoryId);

    List<Category> findByStoreIdAndParentPublicId(String storeId, String parentPublicId);

    Optional<Category> findByStoreIdAndSlug(String storeId, String slug);
}
