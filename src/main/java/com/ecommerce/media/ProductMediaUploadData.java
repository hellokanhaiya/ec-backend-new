package com.ecommerce.media;

public record ProductMediaUploadData(
        String url,
        String fileName,
        String contentType,
        long size,
        String objectName) {}
