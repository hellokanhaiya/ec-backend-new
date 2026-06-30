package com.ecommerce.store;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {
    Optional<StoreProfile> findByPublicUserId(String publicUserId);

    Optional<StoreProfile> findByOwnerPublicUserId(String ownerPublicUserId);

    Optional<StoreProfile> findByOwnerUserId(Long ownerUserId);
}
