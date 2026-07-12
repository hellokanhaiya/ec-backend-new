package com.ecommerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Entity
@Table(
        name = "order_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_settings_store", columnNames = "store_id"))
public class OrderSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, unique = true, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "order_prefix", nullable = false, length = 16)
    private String orderPrefix = "ORD";

    /** Prefix for draft-order numbers, kept on a separate counter from confirmed orders. */
    @Column(name = "draft_prefix", nullable = false, length = 16)
    private String draftPrefix = "DR";

    @Column(name = "order_number_padding", nullable = false)
    private Integer orderNumberPadding = 4;

    /**
     * When true the running order number restarts at 1 at the start of each financial year.
     * Off by default: a fresh store numbers continuously (ORD-0001, ORD-0002, ...). Turning
     * this on is normally paired with {@link #includeFinancialYear} so numbers stay unique.
     */
    @Column(name = "financial_year_reset", nullable = false)
    private boolean financialYearReset = false;

    /** When true the financial-year label (e.g. 2526) is embedded in the order number. */
    @Column(name = "include_financial_year", nullable = false)
    private boolean includeFinancialYear = false;

    /** Month (1-12) the financial year starts on. India defaults to April. */
    @Column(name = "financial_year_start_month", nullable = false)
    private Integer financialYearStartMonth = 4;

    @Column(name = "default_shipping_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal defaultShippingCharge = new BigDecimal("99.00");

    @Column(name = "default_package_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal defaultPackageCharge = BigDecimal.ZERO;

    @Column(name = "free_shipping_threshold", nullable = false, precision = 15, scale = 2)
    private BigDecimal freeShippingThreshold = new BigDecimal("4999.00");

    @Column(name = "default_tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultTaxRate = new BigDecimal("18.00");

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
