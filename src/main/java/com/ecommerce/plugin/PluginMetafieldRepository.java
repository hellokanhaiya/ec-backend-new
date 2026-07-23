package com.ecommerce.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginMetafieldRepository extends JpaRepository<PluginMetafield, Long> {

    Optional<PluginMetafield> findByStoreIdAndNamespaceAndResourceTypeAndResourceIdAndMkey(
            String storeId, String namespace, String resourceType, String resourceId, String mkey);

    List<PluginMetafield> findByStoreIdAndNamespaceAndResourceTypeAndResourceId(
            String storeId, String namespace, String resourceType, String resourceId);

    List<PluginMetafield> findByStoreIdAndResourceTypeAndResourceIdInAndMkeyIn(
            String storeId, String resourceType, Collection<String> resourceIds, Collection<String> mkeys);
}
