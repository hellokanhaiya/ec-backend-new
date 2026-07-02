package com.ecommerce.product;

import java.util.List;

/** Generated image URLs (served directly by the open-source image endpoint) plus
 * the prompt used, so the frontend can add them to the media gallery. */
public record GenerateImageData(List<String> urls, String prompt) {}
