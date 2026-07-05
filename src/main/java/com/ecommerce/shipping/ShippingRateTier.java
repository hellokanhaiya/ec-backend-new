package com.ecommerce.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * A band within a {@link ShippingRate}. The band matches when the driving quantity
 * (cart weight, subtotal, or distance depending on the rate type) falls in
 * {@code [lowerBound, upperBound)}; {@code upperBound} null means "and above". The band
 * charges {@code price} plus {@code perUnitPrice × quantity}.
 */
@Getter
@Setter
@Entity
@Table(name = "shipping_rate_tiers")
public class ShippingRateTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lower_bound", precision = 15, scale = 3)
    private BigDecimal lowerBound;

    @Column(name = "upper_bound", precision = 15, scale = 3)
    private BigDecimal upperBound;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "per_unit_price", precision = 15, scale = 2)
    private BigDecimal perUnitPrice;
}
