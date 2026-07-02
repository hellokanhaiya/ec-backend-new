package com.ecommerce.tax;

import java.math.BigDecimal;

/** A selectable tax class for a product. {@code code} is stored on the product
 * ({@code taxCode}); {@code rate} is the percentage applied at checkout. */
public record TaxRateData(String code, String label, BigDecimal rate, String description) {}
