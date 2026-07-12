package com.ecommerce.billing;

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
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** One subscription record per store — the store's current plan and expiry. */
@Getter
@Setter
@Entity
@Table(
        name = "billing_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_subscriptions_store", columnNames = "store_id"))
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, unique = true, length = 36)
    private String storeId;

    @Column(name = "plan_code", length = 32)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 16)
    private BillingCycle billingCycle;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "credits_remaining", nullable = false)
    private int creditsRemaining;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(name = "last_razorpay_order_id", length = 64)
    private String lastRazorpayOrderId;

    @Column(name = "last_razorpay_payment_id", length = 64)
    private String lastRazorpayPaymentId;

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
