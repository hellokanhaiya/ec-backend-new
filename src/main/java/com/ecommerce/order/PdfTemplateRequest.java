package com.ecommerce.order;

public record PdfTemplateRequest(
        String name,
        String type,
        Boolean isDefault,
        String layoutConfig,
        String headerHtml,
        String footerHtml,
        String logoUrl) {}
