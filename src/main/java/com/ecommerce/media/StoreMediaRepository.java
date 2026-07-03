package com.ecommerce.media;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMediaRepository extends JpaRepository<StoreMedia, Long> {
    List<StoreMedia> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<StoreMedia> findByStoreIdAndPublicMediaId(String storeId, String publicMediaId);

    List<StoreMedia> findByStoreIdAndUrlIn(String storeId, Collection<String> urls);
}
