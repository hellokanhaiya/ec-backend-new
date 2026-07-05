package com.ecommerce.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfTemplateRepository extends JpaRepository<PdfTemplate, Long> {
    Optional<PdfTemplate> findByStoreIdAndPublicTemplateId(String storeId, String publicTemplateId);

    java.util.List<PdfTemplate> findByStoreIdOrderByCreatedAtDesc(String storeId);

    java.util.List<PdfTemplate> findByStoreIdAndTypeOrderByCreatedAtDesc(String storeId, PdfTemplateType type);

    Optional<PdfTemplate> findByStoreIdAndTypeAndDefaultTemplateTrue(String storeId, PdfTemplateType type);
}
