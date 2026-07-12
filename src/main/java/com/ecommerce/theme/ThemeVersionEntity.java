package com.ecommerce.theme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An immutable snapshot of a theme's document, captured on every publish (and
 * available for rollback). Keeps the history stack the builder shows so a
 * merchant can restore or preview any prior state.
 */
@Getter
@Setter
@Entity
@Table(
        name = "storefront_theme_versions",
        indexes = {@Index(name = "idx_theme_version_theme", columnList = "theme_id")})
public class ThemeVersionEntity {
    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "theme_id", length = 40, nullable = false)
    private String themeId;

    @Column(name = "store_id", length = 64, nullable = false)
    private String storeId;

    @Column(name = "label", length = 160)
    private String label;

    /** True when this snapshot was created by a publish (vs a manual checkpoint). */
    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Lob
    @Column(name = "json", nullable = false)
    private String json;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
