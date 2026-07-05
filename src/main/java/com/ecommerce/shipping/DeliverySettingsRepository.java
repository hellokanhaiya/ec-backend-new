package com.ecommerce.shipping;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverySettingsRepository extends JpaRepository<DeliverySettings, Long> {
    Optional<DeliverySettings> findByStoreId(String storeId);
}
