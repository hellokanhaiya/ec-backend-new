package com.ecommerce.product;

import java.util.List;

/**
 * Input for the system "related products" suggestion engine. The backend returns
 * same-category products, excluding the product itself ({@code excludeId}) and any
 * ids the client already has linked or dismissed ({@code excludeIds}).
 */
public record RelatedSuggestionRequest(
        String category,
        String excludeId,
        List<String> excludeIds,
        Integer limit) {}
