package com.ecommerce.promotion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePromotionRepository extends JpaRepository<StorePromotion, Long> {
    List<StorePromotion> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<StorePromotion> findByStoreIdAndPublicPromotionId(String storeId, String publicPromotionId);

    Optional<StorePromotion> findByStoreIdAndCodeIgnoreCase(String storeId, String code);
}
