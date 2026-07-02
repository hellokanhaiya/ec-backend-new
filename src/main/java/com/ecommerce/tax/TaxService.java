package com.ecommerce.tax;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Serves the tax classes a store can assign to a product. This is reference data
 * (not per-store rows), keyed off the store's country. India uses GST slabs keyed
 * by HSN code; other countries currently fall back to a single standard-rate slab
 * that a store can refine later. Modelled after how Shopify/WooCommerce expose a
 * fixed set of tax classes/rates the merchant picks from per product.
 */
@Service
public class TaxService {

    private static final List<TaxRateData> INDIA_GST = List.of(
            new TaxRateData("GST_0", "GST 0% (Exempt)", new BigDecimal("0.00"), "Unprocessed/essential goods"),
            new TaxRateData("GST_5", "GST 5%", new BigDecimal("5.00"), "Household necessities, apparel < ₹1000"),
            new TaxRateData("GST_12", "GST 12%", new BigDecimal("12.00"), "Processed food, apparel ≥ ₹1000"),
            new TaxRateData("GST_18", "GST 18%", new BigDecimal("18.00"), "Most goods and services"),
            new TaxRateData("GST_28", "GST 28%", new BigDecimal("28.00"), "Luxury and sin goods"));

    private static final List<TaxRateData> STANDARD = List.of(
            new TaxRateData("TAX_NONE", "No tax", new BigDecimal("0.00"), "Not taxed"),
            new TaxRateData("TAX_STANDARD", "Standard rate", new BigDecimal("0.00"), "Store-defined standard rate"));

    public TaxRatesData rates(String countryCode) {
        String cc = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
        if (cc.equals("IN") || cc.isEmpty()) {
            return new TaxRatesData("IN", "GST", "HSN code", INDIA_GST);
        }
        return new TaxRatesData(cc, "TAX", "Tax code", STANDARD);
    }
}
