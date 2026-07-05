package com.ecommerce.shipping;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A manually-maintained serviceable pincode range for a zone. A single pincode is a range
 * where {@code fromPincode == toPincode}. Carries per-range COD availability, an ETA window,
 * and an optional straight-line {@code distanceKm} used by distance-based rates when the
 * warehouse/destination lat-long is unknown. Designed so a courier-API provider can replace
 * this manual source later without changing callers.
 */
@Getter
@Setter
@Entity
@Table(
        name = "pincode_ranges",
        uniqueConstraints = @UniqueConstraint(name = "uk_pincode_ranges_public_id", columnNames = "public_pincode_id"),
        indexes = @Index(name = "idx_pincode_ranges_store_zone", columnList = "store_id, zone_public_id"))
public class PincodeRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_pincode_id", nullable = false, unique = true, length = 36)
    private String publicPincodeId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "zone_public_id", nullable = false, length = 36)
    private String zonePublicId;

    /** Optional: restrict this serviceability to a specific warehouse. */
    @Column(name = "warehouse_public_id", length = 36)
    private String warehousePublicId;

    @Column(name = "from_pincode", nullable = false, length = 16)
    private String fromPincode;

    @Column(name = "to_pincode", nullable = false, length = 16)
    private String toPincode;

    @Column(name = "cod_available", nullable = false)
    private boolean codAvailable = false;

    @Column(name = "eta_min_days")
    private Integer etaMinDays;

    @Column(name = "eta_max_days")
    private Integer etaMaxDays;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicPincodeId == null || publicPincodeId.isBlank()) {
            publicPincodeId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
