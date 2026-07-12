package com.ecommerce.vendor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    List<Vendor> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<Vendor> findByStoreIdAndPublicVendorId(String storeId, String publicVendorId);

    long countByStoreId(String storeId);
}
