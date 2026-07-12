package com.ecommerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_line_items")
public class StoreOrderLineItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_public_id", length = 36)
    private String productPublicId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "variant", length = 255)
    private String variant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "image", length = 1000)
    private String image;

    // Per-item (individual) discount entered by the merchant; value is per unit.
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 16)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_reason", length = 255)
    private String discountReason;

    /** Total discount applied to this line (already reflected in lineTotal). */
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
}
