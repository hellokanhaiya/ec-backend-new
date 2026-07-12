package com.ecommerce.bundle;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A bundle or multi-pack: a sellable package composed of existing products.
 * It holds its own selling price; component value, savings and available pack
 * stock are derived from the linked products at read time.
 */
@Getter
@Setter
@Entity
@Table(
        name = "product_bundles",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_bundles_public_id", columnNames = "public_bundle_id"))
public class ProductBundle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_bundle_id", nullable = false, unique = true, length = 36)
    private String publicBundleId;

    @Column(name = "bundle_code", length = 32)
    private String bundleCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private BundleType type = BundleType.BUNDLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private BundleStatus status = BundleStatus.DRAFT;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "image", length = 1024)
    private String image;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "compare_at_price", precision = 15, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol = "₹";

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id")
    @OrderBy("id ASC")
    private List<ProductBundleItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicBundleId == null || publicBundleId.isBlank()) {
            publicBundleId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
