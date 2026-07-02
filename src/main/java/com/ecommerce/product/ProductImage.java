package com.ecommerce.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A product media item (image or AI-generated image URL). Ordered by
 * {@code position}, with exactly one marked primary — the {@code StoreProductService}
 * guarantees that invariant the same way customer addresses guarantee one default.
 * Only the URL is stored here; binary upload/hosting is handled elsewhere.
 */
@Getter
@Setter
@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "position", nullable = false)
    private int position = 0;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
