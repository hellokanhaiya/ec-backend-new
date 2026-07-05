package com.ecommerce.order;

import java.util.Locale;

public enum PdfTemplateType {
    INVOICE,
    SHIPPING_LABEL,
    PACKING_SLIP;

    public static PdfTemplateType from(String value) {
        if (value == null || value.isBlank()) {
            return INVOICE;
        }
        // Accept both "packing_slip" and the hyphenated API form "packing-slip".
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return PdfTemplateType.valueOf(normalized);
    }

    public String apiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
