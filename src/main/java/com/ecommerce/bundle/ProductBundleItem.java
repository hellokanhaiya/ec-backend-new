package com.ecommerce.bundle;

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
 * One component of a bundle / multi-pack: a reference to an existing product
 * (by public id) plus how many units of it the bundle contains. Name/sku/price
 * are snapshotted at save time so the bundle stays readable even if the source
 * product later changes, while the public id keeps the live link for stock.
 */
@Getter
@Setter
@Entity
@Table(name = "product_bundle_items")
public class ProductBundleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "image", length = 1024)
    private String image;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;
}
