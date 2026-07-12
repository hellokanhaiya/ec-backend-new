package com.ecommerce.product;

import java.math.BigDecimal;
import java.util.List;

/** Input DTO for create/update. Boxed types allow "not provided" to mean "leave
 * default"; the service normalises everything before persisting. */
public record ProductRequest(
        String title,
        String slug,
        String summary,
        String description,
        String status,
        String productType,
        String category,
        String categoryPath,
        String categoryPublicId,
        String vendor,
        BigDecimal price,
        BigDecimal compareAtPrice,
        BigDecimal costPerItem,
        String sku,
        String barcode,
        Integer stock,
        Boolean trackInventory,
        Boolean requiresShipping,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String countryOfOrigin,
        Boolean taxable,
        String hsnCode,
        String taxCode,
        BigDecimal taxRate,
        String seoTitle,
        String seoDescription,
        String seoKeyword,
        Boolean createRedirect,
        List<String> tags,
        List<ProductImageRequest> images,
        List<RelatedProductRequest> relatedProducts,
        List<String> dismissedRelatedProductIds) {}
