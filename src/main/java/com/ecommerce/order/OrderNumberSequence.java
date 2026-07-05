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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-store, per-period running counter that backs human-friendly order numbers.
 *
 * <p>When financial-year reset is enabled the {@code periodKey} holds the financial-year
 * label (e.g. {@code "2526"}) so the sequence restarts at 1 each year; when disabled a
 * single {@code "ALL"} row keeps a continuous count for the store. The row is locked for
 * update while a number is allocated so concurrent order creation cannot hand out
 * duplicate numbers.
 */
@Getter
@Setter
@Entity
@Table(
        name = "order_number_sequences",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_order_number_sequences_store_period",
                        columnNames = {"store_id", "period_key"}))
public class OrderNumberSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, length = 36)
    private String storeId;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "last_value", nullable = false)
    private Long lastValue = 0L;

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
