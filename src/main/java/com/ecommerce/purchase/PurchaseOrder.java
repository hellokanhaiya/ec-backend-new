package com.ecommerce.purchase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "purchase_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_orders_public_id", columnNames = "public_purchase_order_id"))
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_purchase_order_id", nullable = false, unique = true, length = 36)
    private String publicPurchaseOrderId;

    @Column(name = "purchase_order_number", length = 32)
    private String purchaseOrderNumber;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "supplier_public_id", length = 36)
    private String supplierPublicId;

    @Column(name = "supplier_name", nullable = false, length = 255)
    private String supplierName;

    @Column(name = "supplier_email", length = 255)
    private String supplierEmail;

    @Column(name = "supplier_phone", length = 64)
    private String supplierPhone;

    @Column(name = "supplier_company", length = 255)
    private String supplierCompany;

    @Column(name = "warehouse_public_id", length = 36)
    private String warehousePublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol = "₹";

    @Column(name = "currency_country_code", nullable = false, length = 8)
    private String currencyCountryCode = "IN";

    @Column(name = "inventory_received_applied", nullable = false)
    private boolean inventoryReceivedApplied;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    @OrderBy("id ASC")
    private List<PurchaseOrderLineItem> lineItems = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "purchase_order_tags", joinColumns = @JoinColumn(name = "purchase_order_id"))
    @Column(name = "tag", length = 80)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicPurchaseOrderId == null || publicPurchaseOrderId.isBlank()) {
            publicPurchaseOrderId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
