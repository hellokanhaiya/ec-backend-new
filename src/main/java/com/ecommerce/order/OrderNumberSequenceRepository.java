package com.ecommerce.order;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderNumberSequenceRepository extends JpaRepository<OrderNumberSequence, Long> {

    /**
     * Loads the counter row for a store/period with a pessimistic write lock so that two
     * orders created at the same instant serialise on this row instead of racing to the
     * same number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderNumberSequence s where s.storeId = :storeId and s.periodKey = :periodKey")
    Optional<OrderNumberSequence> lockByStoreIdAndPeriodKey(
            @Param("storeId") String storeId, @Param("periodKey") String periodKey);

    Optional<OrderNumberSequence> findByStoreIdAndPeriodKey(String storeId, String periodKey);
}
