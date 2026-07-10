package com.ecommerce.bulk;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkJobRepository extends JpaRepository<BulkJob, Long> {
    Optional<BulkJob> findByStoreIdAndPublicJobId(String storeId, String publicJobId);
}
