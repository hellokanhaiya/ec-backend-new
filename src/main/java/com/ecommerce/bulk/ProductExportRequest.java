package com.ecommerce.bulk;

import java.util.List;

/**
 * Selects which products to export. Precedence: if {@code all} is true the whole
 * catalog (optionally narrowed by the same filters as the product list) is
 * exported; otherwise the explicit {@code productIds} are used, falling back to
 * {@code skus}.
 */
public record ProductExportRequest(
        List<String> productIds,
        List<String> skus,
        Boolean all,
        String search,
        String status,
        String category) {}
