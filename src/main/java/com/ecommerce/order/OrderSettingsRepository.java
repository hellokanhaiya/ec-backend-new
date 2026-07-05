package com.ecommerce.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSettingsRepository extends JpaRepository<OrderSettings, Long> {
    Optional<OrderSettings> findByStoreId(String storeId);
}
