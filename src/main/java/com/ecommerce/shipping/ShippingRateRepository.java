package com.ecommerce.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingRateRepository extends JpaRepository<ShippingRate, Long> {
    List<ShippingRate> findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(String storeId, String zonePublicId);

    Optional<ShippingRate> findByStoreIdAndPublicRateId(String storeId, String publicRateId);

    void deleteByStoreIdAndZonePublicId(String storeId, String zonePublicId);
}
