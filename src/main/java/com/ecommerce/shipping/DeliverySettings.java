package com.ecommerce.shipping;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Store-level delivery configuration that sits above zones/rates: dispatch (processing) time
 * that feeds the customer-facing ETA, an origin pincode for distance/courier estimates, and
 * Cash-on-Delivery economics. In the Indian market COD is a first-class capability (with a fee
 * and order-value limits), not a checkbox — modelled here and layered onto the shipping quote.
 * One row per store.
 */
@Getter
@Setter
@Entity
@Table(
        name = "delivery_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_settings_store", columnNames = "store_id"))
public class DeliverySettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    /** Dispatch/processing days added to the transit ETA before it's shown to the buyer. */
    @Column(name = "processing_days", nullable = false)
    private int processingDays = 1;

    /** Origin pincode (dispatch location) — used by distance/courier estimates. */
    @Column(name = "origin_pincode", length = 16)
    private String originPincode;

    /** Optional global free-shipping threshold used for the checkout nudge / marketing copy. */
    @Column(name = "free_shipping_threshold", precision = 15, scale = 2)
    private BigDecimal freeShippingThreshold;

    // --- Cash on delivery ---------------------------------------------------
    @Column(name = "cod_enabled", nullable = false)
    private boolean codEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_fee_type", nullable = false, length = 16)
    private CodFeeType codFeeType = CodFeeType.FLAT;

    /** Flat fee amount, or percentage of order value, depending on {@link #codFeeType}. */
    @Column(name = "cod_fee_value", precision = 15, scale = 2)
    private BigDecimal codFeeValue = BigDecimal.ZERO;

    @Column(name = "cod_min_order", precision = 15, scale = 2)
    private BigDecimal codMinOrder;

    @Column(name = "cod_max_order", precision = 15, scale = 2)
    private BigDecimal codMaxOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
