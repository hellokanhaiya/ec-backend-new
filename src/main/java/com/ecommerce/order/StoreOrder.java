package com.ecommerce.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_public_id", columnNames = "public_order_id"))
public class StoreOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_order_id", nullable = false, unique = true, length = 36)
    private String publicOrderId;

    @Column(name = "order_number", length = 32)
    private String orderNumber;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "owner_public_user_id", nullable = false, length = 36)
    private String ownerPublicUserId;

    @Column(name = "customer_public_id", length = 36)
    private String customerPublicId;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 48)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_status", nullable = false, length = 32)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.PENDING;

    @Column(name = "channel", length = 80)
    private String channel = "Online Store";

    @Column(name = "courier", length = 128)
    private String courier;

    @Column(name = "tracking_number", length = 128)
    private String trackingNumber;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "pincode", length = 32)
    private String pincode;

    @Column(name = "country", length = 80)
    private String country;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol = "₹";

    @Column(name = "currency_country_code", nullable = false, length = 8)
    private String currencyCountryCode = "IN";

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_mode", nullable = false, length = 24)
    private DiscountMode discountMode = DiscountMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 24)
    private DiscountType discountType = DiscountType.FIXED;

    @Column(name = "coupon_code", length = 80)
    private String couponCode;

    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    @Column(name = "discount_value", precision = 15, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingCharge = BigDecimal.ZERO;

    @Column(name = "package_charge", nullable = false, precision = 15, scale = 2)
    private BigDecimal packageCharge = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @OrderBy("id ASC")
    private List<StoreOrderLineItem> lineItems = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_tags", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "tag", length = 80)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicOrderId == null || publicOrderId.isBlank()) {
            publicOrderId = UUID.randomUUID().toString();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
