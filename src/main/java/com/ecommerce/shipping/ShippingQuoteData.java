package com.ecommerce.shipping;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a shipping quote. {@code serviceable} is false when the destination pincode is
 * not covered by any manual pincode range; {@code options} is then empty. COD availability and
 * fee are resolved from the store's delivery settings and the order value, following the India
 * pattern where COD is a separate capability from prepaid serviceability.
 */
public record ShippingQuoteData(
        boolean serviceable, List<ShippingQuoteOptionData> options, boolean codAvailable, BigDecimal codFee) {}
