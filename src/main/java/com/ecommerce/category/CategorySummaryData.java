package com.ecommerce.category;

import java.time.Instant;

/** List-view row. The frontend builds the tree from {@code parentPublicId}. */
public record CategorySummaryData(
        String publicCategoryId,
        String categoryCode,
        String name,
        String slug,
        String image,
        String parentPublicId,
        int productCount,
        boolean active,
        Instant createdAt) {}
