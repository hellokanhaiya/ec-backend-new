package com.ecommerce.order;

import java.time.Instant;

public record PdfTemplateData(
        String id,
        String name,
        String type,
        boolean defaultTemplate,
        boolean systemTemplate,
        String layoutConfig,
        String headerHtml,
        String footerHtml,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt) {}
