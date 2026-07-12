package com.ecommerce.product;

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
 * A lightweight snapshot of another product linked as a "related product" (cross-sell)
 * of the owning product. Mirrors {@code CategoryProduct}: we store only the few display
 * values a related-products strip needs (image, name, sku, price, mrp) rather than a
 * hard relation, so the view is self-contained and cheap to render.
 * {@code publicProductId} links back to the full product.
 */
@Getter
@Setter
@Entity
@Table(name = "product_related")
public class RelatedProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_product_id", length = 36)
    private String publicProductId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "image", length = 1024)
    private String image;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    /** Maximum retail price / compare-at price. */
    @Column(name = "mrp", precision = 15, scale = 2)
    private BigDecimal mrp;

    @Column(name = "position", nullable = false)
    private int position = 0;
}
