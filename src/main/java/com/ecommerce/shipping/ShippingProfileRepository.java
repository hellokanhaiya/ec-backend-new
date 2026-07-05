package com.ecommerce.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingProfileRepository extends JpaRepository<ShippingProfile, Long> {
    List<ShippingProfile> findByStoreIdOrderByCreatedAtAsc(String storeId);

    Optional<ShippingProfile> findByStoreIdAndPublicProfileId(String storeId, String publicProfileId);

    Optional<ShippingProfile> findFirstByStoreIdAndDefaultProfileTrue(String storeId);

    long countByStoreId(String storeId);
}
