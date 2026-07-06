package com.ecommerce.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Full product detail returned by get/create/update. */
public record ProductData(
        String publicProductId,
        String productCode,
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
        String sku,
        String barcode,
        Integer stock,
        boolean trackInventory,
        boolean requiresShipping,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String countryOfOrigin,
        boolean taxable,
        String hsnCode,
        String taxCode,
        BigDecimal taxRate,
        String seoTitle,
        String seoDescription,
        String seoKeyword,
        int salesCount,
        List<String> tags,
        List<ProductImageData> images,
        Instant createdAt,
        Instant updatedAt) {}
