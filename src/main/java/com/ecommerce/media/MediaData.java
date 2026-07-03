package com.ecommerce.media;

import java.time.Instant;
import java.util.List;

public record MediaData(
        String publicMediaId,
        String fileName,
        String url,
        String contentType,
        long size,
        String objectName,
        String label,
        String source,
        List<MediaProductData> products,
        Instant createdAt,
        Instant updatedAt) {}
