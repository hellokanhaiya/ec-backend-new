package com.ecommerce.order;

import java.util.List;

public record PdfTemplateListData(List<PdfTemplateData> items, long total) {}
