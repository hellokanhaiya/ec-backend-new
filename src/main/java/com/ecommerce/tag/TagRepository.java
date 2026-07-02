package com.ecommerce.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByStoreIdOrderByNameAsc(String storeId);

    Optional<Tag> findByStoreIdAndNameIgnoreCase(String storeId, String name);
}
