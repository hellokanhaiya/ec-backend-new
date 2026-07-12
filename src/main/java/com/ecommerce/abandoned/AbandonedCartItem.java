package com.ecommerce.abandoned;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A single product line inside an {@link AbandonedCart}. */
@Getter
@Setter
@Entity
@Table(name = "abandoned_cart_items")
public class AbandonedCartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_public_id", length = 36)
    private String productPublicId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "variant", length = 255)
    private String variant;

    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;
}
