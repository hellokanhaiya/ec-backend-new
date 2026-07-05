package com.ecommerce.warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A fulfillment location / warehouse, scoped to a store via {@code storeId}. A store
 * always has at least one warehouse: the first one is seeded from the store's head-office
 * address ({@code StoreProfile}) and marked default. Per-warehouse stock lives in
 * {@link InventoryLevel}; distance-based shipping rates use the optional lat/long here.
 */
@Getter
@Setter
@Entity
@Table(
        name = "warehouses",
        uniqueConstraints = @UniqueConstraint(name = "uk_warehouses_public_id", columnNames = "public_warehouse_id"))
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_warehouse_id", nullable = false, unique = true, length = 36)
    private String publicWarehouseId;

    @Column(name = "warehouse_code", length = 32)
    private String warehouseCode;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // --- Address ------------------------------------------------------------
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "pincode", length = 32)
    private String pincode;

    /** Optional geo-coordinates, used by distance-based shipping rates. */
    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    // --- Contact ------------------------------------------------------------
    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    // --- Behaviour ----------------------------------------------------------
    @Column(name = "is_default", nullable = false)
    private boolean defaultWarehouse = false;

    @Column(name = "fulfills_online_orders", nullable = false)
    private boolean fulfillsOnlineOrders = true;

    /** Allocation order when auto-selecting a warehouse (lower = preferred). */
    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicWarehouseId == null || publicWarehouseId.isBlank()) {
            publicWarehouseId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
