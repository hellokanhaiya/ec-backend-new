package com.ecommerce.category;

import com.ecommerce.tag.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A product category, scoped to a store. Hierarchy is modelled with
 * {@code parentPublicId} (self-reference by public id), so a category can nest
 * arbitrarily deep. Slug is unique per store; changing it records a
 * {@link CategoryRedirect}. Assigned products are stored as lightweight
 * {@link CategoryProduct} snapshots.
 */
@Getter
@Setter
@Entity
@Table(
        name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_categories_public_id", columnNames = "public_category_id"))
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_category_id", nullable = false, unique = true, length = 36)
    private String publicCategoryId;

    @Column(name = "category_code", length = 32)
    private String categoryCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image", length = 1024)
    private String image;

    /** Public id of the parent category; null = root level. */
    @Column(name = "parent_public_id", length = 36)
    private String parentPublicId;

    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keyword", length = 255)
    private String seoKeyword;

    /** Whether the category is enabled. Disabled categories stay in admin but are
     * hidden from the storefront. Defaults to enabled. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Manual display order within the store (lower = earlier). New categories are
     * appended; the reorder endpoint rewrites these. */
    @Column(name = "sort_position", nullable = false)
    private int sortPosition = 0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @OrderBy("position ASC")
    private List<CategoryProduct> products = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "category_tags",
            joinColumns = @JoinColumn(name = "category_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicCategoryId == null || publicCategoryId.isBlank()) {
            publicCategoryId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
