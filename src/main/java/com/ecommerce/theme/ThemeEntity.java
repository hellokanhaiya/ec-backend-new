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
 * A storefront theme owned by a store. The entire theme document (pages,
 * sections, blocks, settings) is stored as JSON — never HTML — in two columns:
 * {@code draftJson} is the work-in-progress the builder edits, and
 * {@code publishedJson} is the live copy shoppers see. Nothing goes live until
 * a publish copies draft → published (and snapshots a {@link ThemeVersionEntity}).
 */
@Getter
@Setter
@Entity
@Table(
        name = "storefront_themes",
        indexes = {
            @Index(name = "idx_theme_store", columnList = "store_id"),
            @Index(name = "idx_theme_store_active", columnList = "store_id,active")
        })
public class ThemeEntity {
    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "store_id", length = 64, nullable = false)
    private String storeId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "author", length = 80)
    private String author;

    @Column(name = "accent", length = 16)
    private String accent;

    /** True for the single theme currently serving the live storefront. */
    @Column(name = "active", nullable = false)
    private boolean active = false;

    @Lob
    @Column(name = "draft_json", nullable = false)
    private String draftJson;

    @Lob
    @Column(name = "published_json")
    private String publishedJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
