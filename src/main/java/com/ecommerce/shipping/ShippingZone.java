package com.ecommerce.shipping;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A shipping zone within a profile — a named group of regions (e.g. "Domestic", "International")
 * that share a set of rates. Regions are cascade-managed children; rates and pincode ranges
 * reference the zone by {@code publicZoneId}.
 */
@Getter
@Setter
@Entity
@Table(
        name = "shipping_zones",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipping_zones_public_id", columnNames = "public_zone_id"))
public class ShippingZone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_zone_id", nullable = false, unique = true, length = 36)
    private String publicZoneId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "profile_public_id", nullable = false, length = 36)
    private String profilePublicId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Currency rates in this zone are priced in (multi-country support). */
    @Column(name = "currency_code", length = 8)
    private String currencyCode;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private List<ShippingZoneRegion> regions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicZoneId == null || publicZoneId.isBlank()) {
            publicZoneId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
