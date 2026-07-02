package com.ecommerce.category;

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
 * A 301-style slug redirect recorded when a category's slug changes on update, so
 * old category URLs keep resolving. Scoped to a store.
 */
@Getter
@Setter
@Entity
@Table(name = "category_redirects")
public class CategoryRedirect {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "public_category_id", length = 36)
    private String publicCategoryId;

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
