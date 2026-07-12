package com.ecommerce.plugin;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginAppRepository extends JpaRepository<PluginApp, Long> {
    Optional<PluginApp> findByStoreIdAndPublicAppId(String storeId, String publicAppId);
}
