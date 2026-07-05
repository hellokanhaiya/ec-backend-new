package com.ecommerce.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
    List<ShippingZone> findByStoreIdOrderByCreatedAtAsc(String storeId);

    List<ShippingZone> findByStoreIdAndProfilePublicIdOrderByCreatedAtAsc(String storeId, String profilePublicId);

    Optional<ShippingZone> findByStoreIdAndPublicZoneId(String storeId, String publicZoneId);
}
