package com.ecommerce.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** List-view row: the columns the products table renders (name/image, sku,
 * status, category, stock, price/compare, sales, vendor). */
public record ProductSummaryData(
        String publicProductId,
        String productCode,
        String title,
        String image,
        String sku,
        String status,
        String category,
        String vendor,
        Integer stock,
        BigDecimal price,
        BigDecimal compareAtPrice,
        int salesCount,
        List<String> tags,
        Instant createdAt) {}
