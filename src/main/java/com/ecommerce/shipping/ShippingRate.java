package com.ecommerce.shipping;

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
 * A shipping method offered within a zone (e.g. "Standard", "Express"). The price is
 * computed from {@link RateType} using {@code basePrice}/{@code perUnitPrice} or the
 * {@code tiers} bands, and it only applies when the cart's weight and subtotal fall inside
 * the optional min/max conditions. {@code etaMinDays}/{@code etaMaxDays} give the delivery window.
 */
@Getter
@Setter
@Entity
@Table(
        name = "shipping_rates",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipping_rates_public_id", columnNames = "public_rate_id"))
public class ShippingRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_rate_id", nullable = false, unique = true, length = 36)
    private String publicRateId;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "zone_public_id", nullable = false, length = 36)
    private String zonePublicId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 24)
    private RateType rateType = RateType.FLAT;

    @Column(name = "base_price", precision = 15, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    /** Per-kg (weight) or per-km (distance) surcharge on top of {@code basePrice}. */
    @Column(name = "per_unit_price", precision = 15, scale = 2)
    private BigDecimal perUnitPrice;

    // --- conditions ---------------------------------------------------------
    @Column(name = "min_weight", precision = 12, scale = 3)
    private BigDecimal minWeight;

    @Column(name = "max_weight", precision = 12, scale = 3)
    private BigDecimal maxWeight;

    @Column(name = "min_subtotal", precision = 15, scale = 2)
    private BigDecimal minSubtotal;

    @Column(name = "max_subtotal", precision = 15, scale = 2)
    private BigDecimal maxSubtotal;

    // --- delivery window ----------------------------------------------------
    @Column(name = "eta_min_days")
    private Integer etaMinDays;

    @Column(name = "eta_max_days")
    private Integer etaMaxDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_id")
    private List<ShippingRateTier> tiers = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicRateId == null || publicRateId.isBlank()) {
            publicRateId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
