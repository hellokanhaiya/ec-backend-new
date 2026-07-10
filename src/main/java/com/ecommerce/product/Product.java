package com.ecommerce.product;

import com.ecommerce.tag.Tag;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A catalog product, scoped to a store via {@code storeId}. The field set follows
 * the common e-commerce model (Shopify / WooCommerce): identity + content, pricing,
 * inventory, shipping/dimensions, tax, SEO and media. Order-derived metrics
 * (sales / revenue) are placeholders until the orders module lands, mirroring how
 * Customer carries placeholder order metrics.
 */
@Getter
@Setter
@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_public_id", columnNames = "public_product_id"))
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_product_id", nullable = false, unique = true, length = 36)
    private String publicProductId;

    @Column(name = "product_code", length = 32)
    private String productCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    // --- Identity & content -------------------------------------------------
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "slug", length = 255)
    private String slug;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProductStatus status = ProductStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 24)
    private ProductType productType = ProductType.PHYSICAL;

    @Column(name = "category", length = 255)
    private String category;

    /** Full parent → child breadcrumb for context, e.g. "Ethnic Wear > Kurtas". */
    @Column(name = "category_path", length = 512)
    private String categoryPath;

    /** publicCategoryId of the selected category (link to the Category module). */
    @Column(name = "category_public_id", length = 36)
    private String categoryPublicId;

    @Column(name = "vendor", length = 255)
    private String vendor;

    // --- Pricing ------------------------------------------------------------
    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "compare_at_price", precision = 15, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "cost_per_item", precision = 15, scale = 2)
    private BigDecimal costPerItem;

    // --- Inventory ----------------------------------------------------------
    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "barcode", length = 128)
    private String barcode;

    @Column(name = "stock")
    private Integer stock = 0;

    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory = true;

    // --- Shipping & dimensions ---------------------------------------------
    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping = true;

    @Column(name = "weight", precision = 12, scale = 3)
    private BigDecimal weight;

    @Column(name = "length", precision = 12, scale = 3)
    private BigDecimal length;

    @Column(name = "width", precision = 12, scale = 3)
    private BigDecimal width;

    @Column(name = "height", precision = 12, scale = 3)
    private BigDecimal height;

    @Column(name = "country_of_origin", length = 8)
    private String countryOfOrigin;

    // --- Tax ----------------------------------------------------------------
    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Column(name = "hsn_code", length = 32)
    private String hsnCode;

    @Column(name = "tax_code", length = 32)
    private String taxCode;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    // --- SEO ----------------------------------------------------------------
    @Column(name = "seo_title", length = 255)
    private String seoTitle;

    @Column(name = "seo_description", length = 500)
    private String seoDescription;

    @Column(name = "seo_keyword", length = 255)
    private String seoKeyword;

    // --- Order-derived placeholders ----------------------------------------
    @Column(name = "sales_count")
    private Integer salesCount = 0;

    // --- Relations ----------------------------------------------------------
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @OrderBy("position ASC")
    private List<ProductImage> images = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_tags",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicProductId == null || publicProductId.isBlank()) {
            publicProductId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
