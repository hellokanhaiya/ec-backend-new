package com.ecommerce.category;

import java.time.Instant;
import java.util.List;

/** Full category detail returned by get/create/update. */
public record CategoryData(
        String publicCategoryId,
        String categoryCode,
        String name,
        String slug,
        String description,
        String image,
        String parentPublicId,
        String seoTitle,
        String seoDescription,
        String seoKeyword,
        List<String> tags,
        List<CategoryProductData> products,
        int productCount,
        Instant createdAt,
        Instant updatedAt) {}
