package com.ecommerce.category;

import java.time.Instant;

public record CategoryRedirectData(String fromSlug, String toSlug, Instant createdAt) {}
