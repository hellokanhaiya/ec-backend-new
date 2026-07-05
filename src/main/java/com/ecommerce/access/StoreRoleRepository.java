package com.ecommerce.access;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRoleRepository extends JpaRepository<StoreRole, Long> {
    List<StoreRole> findByStoreIdOrderByCreatedAtAsc(String storeId);

    Optional<StoreRole> findByStoreIdAndRoleKey(String storeId, String roleKey);

    Optional<StoreRole> findByStoreIdAndPublicId(String storeId, String publicId);

    boolean existsByStoreId(String storeId);
}
