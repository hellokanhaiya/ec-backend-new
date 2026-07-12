package com.ecommerce.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A single payment attempt for a plan (checkout → paid/failed). Also the renewal ledger. */
@Getter
@Setter
@Entity
@Table(name = "billing_transactions")
public class BillingTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "plan_code", nullable = false, length = 32)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 16)
    private BillingCycle billingCycle;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status = TransactionStatus.CREATED;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
