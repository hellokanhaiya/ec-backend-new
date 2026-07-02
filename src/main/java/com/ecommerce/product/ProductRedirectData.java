package com.ecommerce.product;

import java.time.Instant;

public record ProductRedirectData(
        String publicProductId, String fromSlug, String toSlug, Instant createdAt) {}
