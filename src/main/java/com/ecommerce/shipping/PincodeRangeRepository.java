package com.ecommerce.shipping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PincodeRangeRepository extends JpaRepository<PincodeRange, Long> {
    List<PincodeRange> findByStoreIdOrderByCreatedAtAsc(String storeId);

    List<PincodeRange> findByStoreIdAndZonePublicIdOrderByCreatedAtAsc(String storeId, String zonePublicId);

    Optional<PincodeRange> findByStoreIdAndPublicPincodeId(String storeId, String publicPincodeId);

    void deleteByStoreIdAndZonePublicId(String storeId, String zonePublicId);
}
