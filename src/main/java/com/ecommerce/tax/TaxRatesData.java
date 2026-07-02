package com.ecommerce.tax;

import java.util.List;

/** Envelope describing the tax regime available when creating a product, so the
 * form can render a tax-class dropdown and know which identifier to collect
 * (HSN for India GST). */
public record TaxRatesData(String country, String taxType, String identifierLabel, List<TaxRateData> rates) {}
