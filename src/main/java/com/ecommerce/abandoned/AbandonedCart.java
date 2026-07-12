package com.ecommerce.abandoned;

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
 * A shopper's cart that reached (or started) checkout but was never converted into an order.
 * Scoped to a store exactly like {@link com.ecommerce.order.StoreOrder}.
 */
@Getter
@Setter
@Entity
@Table(
        name = "abandoned_carts",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_abandoned_carts_public_id", columnNames = "public_cart_id"))
public class AbandonedCart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_cart_id", nullable = false, unique = true, length = 36)
    private String publicCartId;

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

    @Column(name = "channel", length = 80)
    private String channel = "Online Store";

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_status", nullable = false, length = 24)
    private RecoveryStatus recoveryStatus = RecoveryStatus.ACTIVE;

    /** How many recovery reminders have been sent to the shopper. */
    @Column(name = "recovery_emails_sent", nullable = false)
    private int recoveryEmailsSent;

    /** Deep link back into the shopper's checkout so they can resume. */
    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol = "₹";

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Total value of the cart the store stands to recover. */
    @Column(name = "cart_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal cartValue = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    @OrderBy("id ASC")
    private List<AbandonedCartItem> lineItems = new ArrayList<>();

    /** When the shopper last touched the cart (used to sort "most recent" abandonment). */
    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Convenience: number of units across all line items. */
    public int totalItems() {
        return lineItems.stream().mapToInt(AbandonedCartItem::getQuantity).sum();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicCartId == null || publicCartId.isBlank()) {
            publicCartId = UUID.randomUUID().toString();
        }
        if (lastActivityAt == null) {
            lastActivityAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
