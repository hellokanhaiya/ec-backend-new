package com.ecommerce.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A 301-style slug redirect recorded when a product's slug changes on update, so
 * old product URLs keep resolving. Scoped to a store. Mirrors CategoryRedirect.
 */
@Getter
@Setter
@Entity
@Table(name = "product_redirects")
public class ProductRedirect {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "public_product_id", length = 36)
    private String publicProductId;

    @Column(name = "from_slug", nullable = false, length = 255)
    private String fromSlug;

    @Column(name = "to_slug", nullable = false, length = 255)
    private String toSlug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
