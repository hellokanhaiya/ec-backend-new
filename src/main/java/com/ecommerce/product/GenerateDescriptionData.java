package com.ecommerce.product;

/** {@code html} is ready to drop into the rich-text editor; {@code source} is
 * "ai" when a model produced it or "fallback" when the template was used. */
public record GenerateDescriptionData(String html, String source) {}
