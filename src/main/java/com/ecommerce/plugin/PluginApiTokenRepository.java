package com.ecommerce.plugin;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginApiTokenRepository extends JpaRepository<PluginApiToken, Long> {
    Optional<PluginApiToken> findByTokenHash(String tokenHash);

    List<PluginApiToken> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<PluginApiToken> findByStoreIdAndPublicTokenId(String storeId, String publicTokenId);
}
