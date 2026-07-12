package com.ecommerce.abandoned;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbandonedCartRepository extends JpaRepository<AbandonedCart, Long> {

    List<AbandonedCart> findByStoreIdOrderByLastActivityAtDesc(String storeId);

    Optional<AbandonedCart> findByStoreIdAndPublicCartId(String storeId, String publicCartId);

    long countByStoreId(String storeId);
}
