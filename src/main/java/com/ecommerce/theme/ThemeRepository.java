package com.ecommerce.theme;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThemeRepository extends JpaRepository<ThemeEntity, String> {
    List<ThemeEntity> findByStoreIdOrderByCreatedAtAsc(String storeId);

    Optional<ThemeEntity> findByIdAndStoreId(String id, String storeId);

    List<ThemeEntity> findByStoreIdAndActiveTrue(String storeId);
}
