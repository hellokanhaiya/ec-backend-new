package com.ecommerce.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Stock of a single product held at a single warehouse. Available quantity is
 * {@code onHand - reserved}. The sum of {@code onHand} across a product's warehouses is
 * rolled up into {@code Product.stock} by {@link StoreInventoryService} so existing
 * product-list / low-stock views keep working without per-warehouse awareness.
 */
@Getter
@Setter
@Entity
@Table(
        name = "inventory_levels",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_inventory_store_warehouse_product",
                        columnNames = {"store_id", "warehouse_public_id", "product_public_id"}),
        indexes = {
            @Index(name = "idx_inventory_store_product", columnList = "store_id, product_public_id"),
            @Index(name = "idx_inventory_store_warehouse", columnList = "store_id, warehouse_public_id")
        })
public class InventoryLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "warehouse_public_id", nullable = false, length = 36)
    private String warehousePublicId;

    @Column(name = "product_public_id", nullable = false, length = 36)
    private String productPublicId;

    @Column(name = "sku", length = 128)
    private String sku;

    @Column(name = "on_hand", nullable = false)
    private int onHand = 0;

    @Column(name = "reserved", nullable = false)
    private int reserved = 0;

    @Column(name = "incoming", nullable = false)
    private int incoming = 0;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public int getAvailable() {
        return onHand - reserved;
    }

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
