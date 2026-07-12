package com.ecommerce.category;

import java.util.List;

public record CategoryRequest(
        String name,
        String slug,
        String description,
        String image,
        String parentPublicId,
        String seoTitle,
        String seoDescription,
        String seoKeyword,
        Boolean active,
        List<String> tags,
        List<CategoryProductRequest> products) {}
