package com.ecommerce.product;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStoreIdOrderByCreatedAtDesc(String storeId);

    Optional<Product> findByStoreIdAndPublicProductId(String storeId, String publicProductId);

    Optional<Product> findByStoreIdAndSkuIgnoreCase(String storeId, String sku);

    @Query(
            """
            select distinct image.url
            from Product product
            join product.images image
            where product.storeId = :storeId
              and product.publicProductId <> :excludedPublicProductId
              and image.url in :urls
            """)
    Set<String> findImageUrlsStillInUseByOtherProducts(
            @Param("storeId") String storeId,
            @Param("excludedPublicProductId") String excludedPublicProductId,
            @Param("urls") Set<String> urls);
}
